package com.msa.account.domain.membership.command.service.impl;

import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.membership.command.service.MembershipCommandService;
import com.msa.account.domain.membership.dto.response.MembershipCancellationResponse;
import com.msa.account.domain.membership.entity.Membership;
import com.msa.account.domain.membership.exception.*;
import com.msa.account.domain.membership.repository.MembershipRepository;
import com.msa.account.domain.outbox.entity.OutboxEvent;
import com.msa.account.domain.outbox.repository.OutboxEventRepository;
import com.msa.account.global.config.membership.MembershipProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 해지와 해지 취소는 처리 전 재요청에 처음 명령 ID를 돌려주고 명령을 한 번만 만듭니다. */
@Service
public class MembershipCommandServiceImpl implements MembershipCommandService {
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final OutboxEventRepository outbox;
    private final MembershipProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public MembershipCommandServiceImpl(
            MembershipRepository memberships,
            AccountRepository accounts,
            OutboxEventRepository outbox,
            MembershipProperties properties,
            ObjectMapper json,
            Clock clock) {
        this.memberships = memberships;
        this.accounts = accounts;
        this.outbox = outbox;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public MembershipCancellationResponse cancel(UUID account) {
        if (!properties.apiEnabled())
            throw new MembershipException(MembershipErrorCode.UNAVAILABLE);
        // 토큰 만료 전이라도 탈퇴했거나 BUYER 권한이 없는 계정은 현재 DB 기준으로 거절합니다.
        var owner = accounts.findById(account).orElse(null);
        if (owner == null
                || owner.getStatus() != AccountStatus.ACTIVE
                || !owner.getRoles().contains(Role.BUYER))
            throw new MembershipException(MembershipErrorCode.NOT_CANCELABLE);
        var m =
                memberships
                        .lock(account)
                        .orElseThrow(
                                () -> new MembershipException(MembershipErrorCode.NOT_CANCELABLE));
        boolean requested =
                m.getCancelGeneration() != null && m.getCancelGeneration() == m.getGeneration();
        if (!requested && (!m.isAutoRenew() || !Set.of("ACTIVE", "GRACE").contains(m.getState())))
            throw new MembershipException(MembershipErrorCode.NOT_CANCELABLE);
        var now = clock.instant();
        UUID id = m.requestCancel(now);
        if (!requested) enqueue(id, "MEMBERSHIP_CANCEL_REQUESTED", m, now);
        return new MembershipCancellationResponse(id, "PENDING", true);
    }

    @Transactional(timeout = 5)
    public MembershipCancellationResponse withdrawCancellation(UUID account) {
        if (!properties.apiEnabled())
            throw new MembershipException(MembershipErrorCode.UNAVAILABLE);
        var owner = accounts.findById(account).orElse(null);
        if (owner == null
                || owner.getStatus() != AccountStatus.ACTIVE
                || !owner.getRoles().contains(Role.BUYER))
            throw new MembershipException(MembershipErrorCode.NOT_WITHDRAWABLE);
        var m =
                memberships
                        .lock(account)
                        .orElseThrow(
                                () ->
                                        new MembershipException(
                                                MembershipErrorCode.NOT_WITHDRAWABLE));
        var now = clock.instant();
        boolean requested = m.getCancelWithdrawCommandId() != null;
        // 혜택 종료 전 해지 예약만 철회합니다. 빌링키 보존 여부는 Payment가 최종 판단합니다.
        if (!requested
                && (!"CANCEL_SCHEDULED".equals(m.getState())
                        || m.isAutoRenew()
                        || !m.entitled(now)))
            throw new MembershipException(MembershipErrorCode.NOT_WITHDRAWABLE);
        UUID id = m.requestCancelWithdrawal(now);
        if (!requested) enqueue(id, "MEMBERSHIP_CANCEL_WITHDRAW_REQUESTED", m, now);
        return new MembershipCancellationResponse(id, "PENDING", true);
    }

    private void enqueue(UUID id, String type, Membership m, Instant now) {
        var command = new LinkedHashMap<String, Object>();
        command.put("eventId", id);
        command.put("schemaVersion", 1);
        command.put("type", type);
        command.put("accountId", m.getAccountId());
        command.put("subscriptionId", m.getSubscriptionId());
        command.put("generation", m.getGeneration());
        command.put("commandVersion", m.getCommandVersion());
        command.put("occurredAt", now);
        // 계정 단위 파티션 키로 같은 계정의 명령 순서를 유지합니다.
        outbox.save(
                OutboxEvent.builder()
                        .id(id)
                        .topic("stockdrop.membership.commands.private.v1")
                        .aggregateId(m.getAccountId())
                        .payload(json.writeValueAsString(command))
                        .now(now)
                        .build());
    }
}
