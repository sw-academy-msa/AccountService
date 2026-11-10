package com.msa.account.domain.account.command.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.account.domain.account.command.dto.request.WithdrawAccountRequest;
import com.msa.account.domain.account.command.service.*;
import com.msa.account.domain.account.entity.*;
import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.account.repository.*;
import com.msa.account.domain.auth.entity.AuthSession;
import com.msa.account.domain.auth.repository.AuthSessionRepository;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** 실제 Account DB와 RSA 발급, Eureka 대역과 로컬 HTTP로 탈퇴 준비/커밋/복구를 연결합니다. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "account.withdrawal.enabled=true",
            "account.withdrawal.poll-ms=3600000",
            "ACCOUNT_WITHDRAWAL_SUBJECT=11111111-1111-1111-1111-111111111117"
        })
@Import(TestcontainersConfiguration.class)
class WithdrawalFlowIntegrationTest {
    static final Map<String, HttpServer> SERVERS = new HashMap<>();
    static final AtomicReference<RSAKey> KEY = new AtomicReference<>();
    static final AtomicLong ORDERS = new AtomicLong(),
            SALES = new AtomicLong(),
            PENDING = new AtomicLong();
    static final AtomicBoolean FAILURE = new AtomicBoolean(), RELEASE_FAILURE = new AtomicBoolean();
    static final AtomicInteger CALLS = new AtomicInteger(), PAGES = new AtomicInteger();

    static {
        try {
            for (String service :
                    List.of(
                            "order-command-service",
                            "product-command-service",
                            "payment-service")) {
                var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext(
                        "/",
                        e -> {
                            CALLS.incrementAndGet();
                            int status = 200;
                            String data = "null";
                            try {
                                String header = e.getRequestHeaders().getFirst("Authorization");
                                var jwt = SignedJWT.parse(header.substring(7));
                                if (KEY.get() == null
                                        || !jwt.verify(
                                                new RSASSAVerifier(KEY.get().toRSAPublicKey()))
                                        || !"account-service"
                                                .equals(
                                                        jwt.getJWTClaimsSet()
                                                                .getStringClaim("client_id"))
                                        || !"account.withdrawal"
                                                .equals(
                                                        jwt.getJWTClaimsSet()
                                                                .getStringClaim("scope")))
                                    status = 401;
                            } catch (Exception ex) {
                                status = 401;
                            }
                            boolean release = e.getRequestMethod().equals("DELETE");
                            String path = e.getRequestURI().getPath();
                            if (release && RELEASE_FAILURE.get()
                                    || service.equals("product-command-service") && FAILURE.get())
                                status = 503;
                            if (service.equals("payment-service"))
                                data = "{\"pendingPayments\":" + PENDING.get() + "}";
                            else if (path.endsWith("/payments")) {
                                int page =
                                        Integer.parseInt(e.getRequestURI().getQuery().substring(5));
                                PAGES.incrementAndGet();
                                data =
                                        page == 0
                                                ? "{\"references\":[{\"orderId\":\"11111111-1111-1111-1111-111111111111\",\"paymentAttemptId\":\"22222222-2222-2222-2222-222222222222\",\"approved\":true}],\"hasNext\":true}"
                                                : "{\"references\":[],\"hasNext\":false}";
                            } else if (!release)
                                data =
                                        service.equals("order-command-service")
                                                ? "{\"activeOrders\":"
                                                        + ORDERS.get()
                                                        + ",\"pendingSettlements\":"
                                                        + 0
                                                        + "}"
                                                : "{\"activeSales\":"
                                                        + SALES.get()
                                                        + ",\"activeReservations\":0}";
                            e.getRequestBody().readAllBytes();
                            byte[] body =
                                    ("{\"success\":true,\"data\":" + data + "}")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            e.getResponseHeaders().set("Content-Type", "application/json");
                            e.sendResponseHeaders(status, body.length);
                            e.getResponseBody().write(body);
                            e.close();
                        });
                server.start();
                SERVERS.put(service, server);
            }
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @AfterAll
    static void stop() {
        SERVERS.values().forEach(server -> server.stop(0));
    }

    @Autowired AccountWithdrawalService service;
    @Autowired WithdrawalEligibilityService eligibility;
    @Autowired WithdrawalEligibilityServiceImpl recovery;
    @Autowired WithdrawalOperationService operations;
    @Autowired WithdrawalOperationRepository requests;
    @Autowired AccountRepository accounts;
    @Autowired AccountWithdrawalRepository history;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired RSAKey signingKey;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean LoadBalancerClient balancer;
    @MockitoBean Clock clock;
    Instant now;
    Account account;

    @BeforeEach
    void setup() {
        now = Instant.parse("2030-01-01T00:00:00Z");
        when(clock.instant()).thenAnswer(i -> now);
        KEY.set(signingKey);
        ORDERS.set(0);
        SALES.set(0);
        PENDING.set(0);
        FAILURE.set(false);
        RELEASE_FAILURE.set(false);
        CALLS.set(0);
        PAGES.set(0);
        when(balancer.choose(anyString()))
                .thenAnswer(
                        i -> {
                            String name = i.getArgument(0);
                            return new DefaultServiceInstance(
                                    "test",
                                    name,
                                    "127.0.0.1",
                                    SERVERS.get(name).getAddress().getPort(),
                                    false);
                        });
        account =
                Account.registerBuyer(
                        UUID.randomUUID() + "@example.com",
                        passwords.encode("Password!123"),
                        "합성 탈퇴 회원");
        account.initializeSignup(now, Set.of(), true, true, true, now);
        account = accounts.saveAndFlush(account);
    }

    WithdrawAccountRequest request() {
        return new WithdrawAccountRequest("Password!123", "합성 테스트", true);
    }

    @Test
    void clearAccountWithdrawsAndRevokesRefreshWithDurableCommit() {
        var session =
                sessions.saveAndFlush(
                        AuthSession.builder()
                                .account(account)
                                .now(now)
                                .expiresAt(now.plusSeconds(3600))
                                .build());
        var result = service.withdraw(account.getId(), request());
        assertThat(result.withdrawnAt()).isEqualTo(now);
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(sessions.findById(session.getId()).orElseThrow().isActive(now)).isFalse();
        assertThat(requests.findById(account.getId()).orElseThrow().getState())
                .isEqualTo("COMMITTED");
        assertThat(PAGES.get()).isEqualTo(2);
        int calls = CALLS.get();
        assertThat(service.withdraw(account.getId(), request()).withdrawnAt()).isEqualTo(now);
        assertThat(CALLS.get()).isEqualTo(calls);
    }

    /** 완료된 판매의 결제 참조가 있어도 수수료 기능 부재만으로 판매자를 차단하지 않습니다. */
    @Test
    void completedSellerPaymentReferenceAllowsWithdrawal() {
        account.grantRole(Role.SELLER, null, now);
        account = accounts.saveAndFlush(account);
        assertThat(service.withdraw(account.getId(), request()).withdrawnAt()).isEqualTo(now);
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(PAGES.get()).isEqualTo(2);
    }

    @Test
    void existingTransactionsOrUnresolvedPaymentsBlockAndRelease() {
        ORDERS.set(1);
        assertThatThrownBy(() -> service.withdraw(account.getId(), request()))
                .isInstanceOf(AccountException.class);
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(requests.findById(account.getId()).orElseThrow().getState())
                .isEqualTo("ABORTED");
        ORDERS.set(0);
        PENDING.set(1);
        assertThatThrownBy(() -> service.withdraw(account.getId(), request()))
                .isInstanceOf(AccountException.class);
        assertThat(history.findById(account.getId())).isEmpty();
    }

    @Test
    void failureKeepsAccountAndDurablyRetriesFenceRelease() {
        FAILURE.set(true);
        RELEASE_FAILURE.set(true);
        var session =
                sessions.saveAndFlush(
                        AuthSession.builder()
                                .account(account)
                                .now(now)
                                .expiresAt(now.plusSeconds(3600))
                                .build());
        assertThatThrownBy(() -> service.withdraw(account.getId(), request()))
                .isInstanceOf(AccountException.class);
        assertThat(requests.findById(account.getId()).orElseThrow().getState())
                .isEqualTo("ABORTING");
        assertThat(sessions.findById(session.getId()).orElseThrow().isActive(now)).isTrue();
        FAILURE.set(false);
        RELEASE_FAILURE.set(false);
        now = now.plusSeconds(11);
        recovery.recover();
        assertThat(requests.findById(account.getId()).orElseThrow().getState())
                .isEqualTo("ABORTED");
    }

    @Test
    void crashedPreparationExpiresAndCannotCommitAfterRecovery() {
        var lease = operations.begin(account.getId()).orElseThrow();
        operations.clear(lease);
        now = now.plusSeconds(121);
        recovery.recover();
        assertThat(requests.findById(account.getId()).orElseThrow().getState())
                .isEqualTo("ABORTED");
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                s ->
                                                        operations.commit(
                                                                lease.accountId(),
                                                                lease.operationId())))
                .isInstanceOf(AccountException.class);
    }

    @Test
    void repeatedConcurrentPreparationCannotStealFirstLease() {
        var first = operations.begin(account.getId()).orElseThrow();
        assertThat(operations.begin(account.getId())).isEmpty();
        assertThat(requests.findById(account.getId()).orElseThrow().getOperationId())
                .isEqualTo(first.operationId());
    }

    @Test
    void wrongPasswordNeverCallsOtherServices() {
        assertThatThrownBy(
                        () ->
                                service.withdraw(
                                        account.getId(),
                                        new WithdrawAccountRequest("wrong", "", true)))
                .isInstanceOf(RuntimeException.class);
        assertThat(CALLS.get()).isZero();
        assertThat(requests.findById(account.getId())).isEmpty();
    }
}
