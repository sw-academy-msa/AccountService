package com.msa.account.domain.membership.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.command.service.AccessTokenService;
import com.msa.account.domain.membership.command.service.MembershipCommandService;
import com.msa.account.domain.membership.exception.*;
import com.msa.account.domain.membership.query.service.MembershipQueryService;
import com.msa.account.domain.membership.repository.*;
import com.msa.account.domain.membership.service.MembershipProjectionService;
import com.msa.account.domain.outbox.repository.OutboxEventRepository;
import com.msa.account.global.config.database.TestcontainersConfiguration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 PostgreSQL에서 결과 이벤트 멱등과 버전 역순, 해지 명령, 만료 예정 알림, JWT claim을 검증합니다. */
@SpringBootTest(properties = {"spring.profiles.active=test", "account.membership.api-enabled=true"})
@Import(TestcontainersConfiguration.class)
class MembershipProjectionIntegrationTest {
    @Autowired MembershipProjectionService projection;
    @Autowired MembershipCommandService commands;
    @Autowired MembershipQueryService queries;
    @Autowired MembershipRepository memberships;
    @Autowired OutboxEventRepository outbox;
    @Autowired AccessTokenService tokens;
    @Autowired AccountRepository accounts;
    @Autowired JwtDecoder decoder;
    @Autowired ObjectMapper json;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private UUID account;
    private UUID subscription;

    @BeforeEach
    void setUp() {
        now.set(Instant.parse("2030-01-10T00:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
        account = UUID.randomUUID();
        subscription = UUID.randomUUID();
    }

    private byte[] event(
            UUID eventId,
            String type,
            long version,
            long cycle,
            String state,
            boolean autoRenew,
            Instant until) {
        var e = new LinkedHashMap<String, Object>();
        e.put("eventId", eventId);
        e.put("schemaVersion", 1);
        e.put("type", type);
        e.put("source", "PAYMENT");
        e.put("accountId", account);
        e.put("subscriptionId", subscription);
        e.put("generation", 1);
        e.put("aggregateVersion", version);
        e.put("cycleNumber", cycle);
        e.put("state", state);
        e.put("autoRenew", autoRenew);
        e.put("periodStart", Instant.parse("2030-01-01T00:00:00Z"));
        e.put("periodEnd", until);
        e.put("entitlementUntil", until);
        e.put("graceUntil", "GRACE".equals(state) ? until : null);
        e.put("amount", 4900);
        e.put("currency", "KRW");
        e.put("planVersion", "monthly-v1");
        e.put("occurredAt", now.get());
        return json.writeValueAsBytes(e);
    }

    private void receive(byte[] payload) {
        projection.receive(account.toString(), payload);
    }

    @Test
    void startedEventMakesClaimActiveUntilEntitlementEnds() {
        Instant until = Instant.parse("2030-02-01T00:00:00Z");
        receive(event(UUID.randomUUID(), "MEMBERSHIP_STARTED", 1, 1, "ACTIVE", true, until));
        var claim = queries.claim(account);
        assertThat(claim)
                .containsEntry("active", true)
                .containsEntry("state", "ACTIVE")
                .containsEntry("valid_until", until.getEpochSecond())
                .containsEntry("version", 1L);
        assertThat(queries.get(account).active()).isTrue();
        // 만료 이벤트가 늦어도 혜택 종료 시각 뒤에는 비회원으로 서명합니다.
        now.set(until);
        assertThat(queries.claim(account))
                .containsEntry("active", false)
                .containsEntry("valid_until", 0L);
    }

    @Test
    void newerSnapshotWinsAndDuplicatesAreIdempotent() {
        UUID renewed = UUID.randomUUID();
        Instant feb = Instant.parse("2030-02-01T00:00:00Z");
        Instant mar = Instant.parse("2030-03-01T00:00:00Z");
        byte[] second = event(renewed, "MEMBERSHIP_RENEWED", 2, 2, "ACTIVE", true, mar);
        receive(second);
        // 역순 도착한 과거 버전은 수신 기록만 남기고 반영하지 않습니다.
        receive(event(UUID.randomUUID(), "MEMBERSHIP_STARTED", 1, 1, "ACTIVE", true, feb));
        receive(second);
        var m = memberships.findById(account).orElseThrow();
        assertThat(m.getSourceVersion()).isEqualTo(2);
        assertThat(m.getEntitlementUntil()).isEqualTo(mar);
        // 같은 eventId의 다른 본문과 같은 버전의 다른 eventId는 계약 오류입니다.
        assertThatThrownBy(
                        () ->
                                receive(
                                        event(
                                                renewed,
                                                "MEMBERSHIP_RENEWED",
                                                2,
                                                2,
                                                "GRACE",
                                                true,
                                                mar)))
                .isInstanceOf(InvalidMembershipEventException.class);
        assertThatThrownBy(
                        () ->
                                receive(
                                        event(
                                                UUID.randomUUID(),
                                                "MEMBERSHIP_RENEWED",
                                                2,
                                                2,
                                                "ACTIVE",
                                                true,
                                                mar)))
                .isInstanceOf(InvalidMembershipEventException.class);
        assertThatThrownBy(
                        () ->
                                projection.receive(
                                        UUID.randomUUID().toString(),
                                        event(
                                                UUID.randomUUID(),
                                                "MEMBERSHIP_RENEWED",
                                                3,
                                                3,
                                                "ACTIVE",
                                                true,
                                                mar)))
                .isInstanceOf(InvalidMembershipEventException.class);
    }

    @Test
    void failureAndRenewalNoticesUseSeparateAggregates() {
        Instant feb = Instant.parse("2030-02-01T00:00:00Z");
        receive(event(UUID.randomUUID(), "MEMBERSHIP_PAYMENT_FAILED", 1, 2, "GRACE", true, feb));
        receive(event(UUID.randomUUID(), "MEMBERSHIP_RENEWED", 2, 2, "ACTIVE", true, feb));
        var rows =
                outbox.findAll().stream()
                        .filter(r -> "stockdrop.notifications.membership.v1".equals(r.topic))
                        .map(r -> json.readTree(r.payload))
                        .filter(n -> account.toString().equals(n.path("accountId").asText()))
                        .toList();
        assertThat(rows).hasSize(2);
        assertThat(rows)
                .extracting(n -> n.path("notificationType").asText())
                .containsExactlyInAnyOrder("MEMBERSHIP_PAYMENT_FAILED", "MEMBERSHIP_RENEWED");
        assertThat(rows).allMatch(n -> "MEMBERSHIP".equals(n.path("source").asText()));
        assertThat(rows).allMatch(n -> "MEMBERSHIP".equals(n.path("targetType").asText()));
        assertThat(rows.get(0).path("aggregateId").asText())
                .isNotEqualTo(rows.get(1).path("aggregateId").asText());
        assertThat(rows.get(0).propertyNames())
                .doesNotContain("billingKey", "customerKey", "paymentKey", "email");
    }

    @Test
    void cancellationWritesOneCommandPerGeneration() {
        var buyer =
                Account.registerBuyer(
                        "cancel-" + UUID.randomUUID() + "@example.com", "hash", "김해지");
        buyer.initializeSignup(now.get(), Set.of(), false, false, false, now.get());
        account = accounts.saveAndFlush(buyer).getId();
        receive(
                event(
                        UUID.randomUUID(),
                        "MEMBERSHIP_STARTED",
                        1,
                        1,
                        "ACTIVE",
                        true,
                        Instant.parse("2030-02-01T00:00:00Z")));
        var first = commands.cancel(account);
        var again = commands.cancel(account);
        assertThat(again.commandId()).isEqualTo(first.commandId());
        assertThat(first.refreshRequired()).isTrue();
        var row = outbox.findById(first.commandId()).orElseThrow();
        assertThat(row.topic).isEqualTo("stockdrop.membership.commands.private.v1");
        assertThat(row.partitionKey).isEqualTo(account.toString());
        var body = json.readTree(row.payload);
        assertThat(body.path("type").asText()).isEqualTo("MEMBERSHIP_CANCEL_REQUESTED");
        assertThat(body.path("subscriptionId").asText()).isEqualTo(subscription.toString());
        assertThat(body.path("generation").isIntegralNumber()).isTrue();
        assertThat(body.path("commandVersion").asLong()).isEqualTo(1);
        assertThat(
                        outbox.findAll().stream()
                                .filter(
                                        r ->
                                                r.topic.equals(
                                                                "stockdrop.membership.commands.private.v1")
                                                        && r.partitionKey.equals(
                                                                account.toString()))
                                .count())
                .isEqualTo(1);
        assertThatThrownBy(() -> commands.cancel(UUID.randomUUID()))
                .isInstanceOf(MembershipException.class);
    }

    @Test
    void expiringNoticeIsCreatedOncePerPaidPeriod() {
        Instant until = now.get().plus(Duration.ofDays(2));
        receive(
                event(
                        UUID.randomUUID(),
                        "MEMBERSHIP_CANCELLATION_SCHEDULED",
                        1,
                        1,
                        "CANCEL_SCHEDULED",
                        false,
                        until));
        assertThat(projection.notifyExpiring()).isGreaterThanOrEqualTo(1);
        assertThat(projection.notifyExpiring()).isZero();
        assertThat(
                        outbox.findAll().stream()
                                .map(r -> json.readTree(r.payload))
                                .filter(
                                        n ->
                                                account.toString()
                                                                .equals(
                                                                        n.path("accountId")
                                                                                .asText())
                                                        && "MEMBERSHIP_EXPIRING"
                                                                .equals(
                                                                        n.path("notificationType")
                                                                                .asText()))
                                .count())
                .isEqualTo(1);
    }

    @Test
    void userAccessTokenCarriesMembershipClaim() {
        var buyer =
                Account.registerBuyer(
                        "member-" + UUID.randomUUID() + "@example.com", "hash", "김회원");
        buyer.initializeSignup(now.get(), Set.of(), false, false, false, now.get());
        buyer = accounts.saveAndFlush(buyer);
        account = buyer.getId();
        Instant until = Instant.parse("2030-02-01T00:00:00Z");
        receive(event(UUID.randomUUID(), "MEMBERSHIP_STARTED", 1, 1, "ACTIVE", true, until));
        when(clock.instant()).thenAnswer(i -> Instant.now());
        var jwt = decoder.decode(tokens.issue(buyer).accessToken());
        Map<String, Object> claim = jwt.getClaim("membership");
        assertThat(claim).containsEntry("active", true).containsEntry("state", "ACTIVE");
        assertThat(((Number) claim.get("valid_until")).longValue())
                .isEqualTo(until.getEpochSecond());
        assertThat(((Number) claim.get("version")).longValue()).isEqualTo(1);
    }
}
