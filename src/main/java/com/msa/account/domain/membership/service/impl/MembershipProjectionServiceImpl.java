package com.msa.account.domain.membership.service.impl;

import com.msa.account.domain.membership.dto.request.MembershipResultEvent;
import com.msa.account.domain.membership.entity.*;
import com.msa.account.domain.membership.exception.InvalidMembershipEventException;
import com.msa.account.domain.membership.repository.*;
import com.msa.account.domain.membership.service.MembershipProjectionService;
import com.msa.account.domain.notification.service.BusinessNoticeService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** 수신 기록과 projection과 알림을 같은 트랜잭션에 커밋하고 높은 원본 버전만 반영합니다. */
@Service
public class MembershipProjectionServiceImpl implements MembershipProjectionService {
    private static final Set<String> TYPES =
            Set.of(
                    "MEMBERSHIP_STARTED",
                    "MEMBERSHIP_RENEWED",
                    "MEMBERSHIP_RENEWAL_PENDING",
                    "MEMBERSHIP_PAYMENT_FAILED",
                    "MEMBERSHIP_CANCELLATION_SCHEDULED",
                    "MEMBERSHIP_EXPIRED",
                    "MEMBERSHIP_WITHDRAWN");
    private static final Set<String> STATES =
            Set.of("PENDING", "ACTIVE", "GRACE", "CANCEL_SCHEDULED", "EXPIRED");
    private final MembershipRepository memberships;
    private final MembershipEventReceiptRepository receipts;
    private final MembershipNoticeReceiptRepository notices;
    private final BusinessNoticeService alerts;
    private final ObjectMapper json;
    private final Clock clock;

    public MembershipProjectionServiceImpl(
            MembershipRepository memberships,
            MembershipEventReceiptRepository receipts,
            MembershipNoticeReceiptRepository notices,
            BusinessNoticeService alerts,
            ObjectMapper json,
            Clock clock) {
        this.memberships = memberships;
        this.receipts = receipts;
        this.notices = notices;
        this.alerts = alerts;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public void receive(String key, byte[] payload) {
        var e = parse(key, payload);
        String hash = hash(payload);
        // 최초 행 생성 경쟁도 계정 단위로 직렬화합니다.
        memberships.lockCreation("membership:" + e.accountId());
        var receipt = receipts.findById(e.eventId()).orElse(null);
        if (receipt != null) {
            if (!receipt.getPayloadHash().equals(hash)) throw invalid();
            return;
        }
        if (receipts.existsBySubscriptionIdAndSourceVersion(
                e.subscriptionId(), e.aggregateVersion())) throw invalid();
        var now = clock.instant();
        var m = memberships.lock(e.accountId()).orElse(null);
        if (m == null)
            m =
                    memberships.saveAndFlush(
                            Membership.builder()
                                    .accountId(e.accountId())
                                    .subscriptionId(e.subscriptionId())
                                    .now(now)
                                    .build());
        // Payment는 계정마다 구독 행 하나를 세대로 재사용합니다. 다른 구독 ID는 계약 오류입니다.
        if (!m.getSubscriptionId().equals(e.subscriptionId())) throw invalid();
        boolean applied =
                m.apply(
                        e.subscriptionId(),
                        e.generation(),
                        e.aggregateVersion(),
                        e.cycleNumber(),
                        e.state(),
                        e.autoRenew(),
                        e.periodStart(),
                        e.periodEnd(),
                        e.entitlementUntil(),
                        e.graceUntil(),
                        e.planVersion(),
                        e.amount(),
                        e.currency(),
                        now);
        receipts.save(
                MembershipEventReceipt.builder()
                        .eventId(e.eventId())
                        .subscriptionId(e.subscriptionId())
                        .sourceVersion(e.aggregateVersion())
                        .payloadHash(hash)
                        .processedAt(now)
                        .build());
        // 늦게 도착한 과거 버전은 수신 기록만 남기고 지난 알림을 만들지 않습니다.
        if (applied) notify(e);
    }

    @Transactional(timeout = 5)
    public int notifyExpiring() {
        var now = clock.instant();
        var until = now.plus(Duration.ofDays(3));
        int created = 0;
        for (UUID account : memberships.findExpiring(now, until, PageRequest.of(0, 25))) {
            var m = memberships.lock(account).orElse(null);
            if (m == null
                    || !"CANCEL_SCHEDULED".equals(m.getState())
                    || !m.entitled(now)
                    || m.getEntitlementUntil().isAfter(until)
                    || notices.existsBySubscriptionIdAndGenerationAndCycleNumberAndNoticeType(
                            m.getSubscriptionId(),
                            m.getGeneration(),
                            m.getCycleNumber(),
                            "MEMBERSHIP_EXPIRING")) continue;
            notices.save(
                    MembershipNoticeReceipt.builder()
                            .id(UUID.randomUUID())
                            .subscriptionId(m.getSubscriptionId())
                            .generation(m.getGeneration())
                            .cycleNumber(m.getCycleNumber())
                            .noticeType("MEMBERSHIP_EXPIRING")
                            .scheduledFor(m.getEntitlementUntil().minus(Duration.ofDays(3)))
                            .createdAt(now)
                            .build());
            alerts.enqueueMembership(
                    account,
                    "MEMBERSHIP_EXPIRING",
                    aggregate(
                            "MEMBERSHIP_EXPIRING",
                            m.getSubscriptionId(),
                            m.getGeneration(),
                            m.getCycleNumber()),
                    m.getSourceVersion(),
                    m.getSubscriptionId(),
                    m.getEntitlementUntil());
            created++;
        }
        return created;
    }

    private void notify(MembershipResultEvent e) {
        String kind =
                switch (e.type()) {
                    case "MEMBERSHIP_PAYMENT_FAILED" -> "MEMBERSHIP_PAYMENT_FAILED";
                    case "MEMBERSHIP_RENEWED" -> "MEMBERSHIP_RENEWED";
                    case "MEMBERSHIP_EXPIRED" -> "MEMBERSHIP_EXPIRED";
                    default -> null;
                };
        if (kind == null) return;
        Instant deadline =
                switch (kind) {
                    case "MEMBERSHIP_PAYMENT_FAILED" -> e.graceUntil();
                    case "MEMBERSHIP_RENEWED" -> e.periodEnd();
                    default -> null;
                };
        // 청구 기간과 알림 종류별로 다른 aggregate를 써서 실패와 갱신 알림이 서로 덮이지 않게 합니다.
        alerts.enqueueMembership(
                e.accountId(),
                kind,
                aggregate(kind, e.subscriptionId(), e.generation(), e.cycleNumber()),
                e.aggregateVersion(),
                e.subscriptionId(),
                deadline);
    }

    private UUID aggregate(String kind, UUID subscription, long generation, long cycle) {
        return UUID.nameUUIDFromBytes(
                ("MEMBERSHIP:" + kind + ":" + subscription + ":" + generation + ":" + cycle)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private MembershipResultEvent parse(String key, byte[] payload) {
        if (payload == null || payload.length > 16384) throw invalid();
        MembershipResultEvent e;
        try {
            e =
                    json.readerFor(MembershipResultEvent.class)
                            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(payload);
        } catch (RuntimeException failure) {
            throw invalid();
        }
        if (e == null
                || e.eventId() == null
                || !Integer.valueOf(1).equals(e.schemaVersion())
                || !"PAYMENT".equals(e.source())
                || !TYPES.contains(e.type())
                || e.accountId() == null
                || !e.accountId().toString().equals(key)
                || e.subscriptionId() == null
                || e.generation() == null
                || e.generation() < 1
                || e.aggregateVersion() == null
                || e.aggregateVersion() < 1
                || e.cycleNumber() == null
                || e.cycleNumber() < 0
                || !STATES.contains(e.state())
                || e.autoRenew() == null
                || e.amount() == null
                || e.amount() < 0
                || !"KRW".equals(e.currency())
                || e.planVersion() == null
                || e.planVersion().length() > 40) throw invalid();
        return e;
    }

    private InvalidMembershipEventException invalid() {
        return new InvalidMembershipEventException();
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 미지원");
        }
    }
}
