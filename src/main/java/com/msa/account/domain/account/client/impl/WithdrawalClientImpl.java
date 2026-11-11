package com.msa.account.domain.account.client.impl;

import com.msa.account.domain.account.client.WithdrawalClient;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.auth.command.service.AccessTokenService;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.*;

import java.net.URI;
import java.time.*;
import java.util.*;

/** Account는 자신이 보유한 기존 issuer로 제한된 서비스 JWT를 발급합니다. 새 로그인이나 개인키 복사는 없습니다. */
@Component
public class WithdrawalClientImpl implements WithdrawalClient {
    private final RestClient rest;
    private final AccessTokenService tokens;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String subject;
    private final boolean enabled;
    private String cached;
    private Instant expiresAt = Instant.MIN;

    public WithdrawalClientImpl(
            @Qualifier("loadBalancedRestClient") RestClient rest,
            AccessTokenService tokens,
            ObjectMapper mapper,
            Clock clock,
            @Value("${ACCOUNT_WITHDRAWAL_SUBJECT:}") String subject,
            @Value("${account.withdrawal.enabled:false}") boolean enabled) {
        this.rest = rest;
        this.tokens = tokens;
        this.mapper = mapper;
        this.clock = clock;
        this.subject = subject;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled && !subject.isBlank();
    }

    public JsonNode exchange(String service, String method, String path, Object body) {
        if (!enabled()
                || !Set.of("product-command-service", "order-command-service", "payment-service")
                        .contains(service)
                || !path.startsWith("/internal/v1/account-withdrawals/")) throw unavailable();
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive())
            throw new IllegalStateException("탈퇴 HTTP는 DB 트랜잭션 밖에서 호출해야 합니다.");
        try {
            // 서비스 ID 주소로 보내 LoadBalancer가 인스턴스를 고르고 상태 확인·연결 실패 재시도를 적용합니다.
            // https로 등록된 인스턴스는 LoadBalancer가 주소를 https로 바꾸므로 기존 Eureka 등록 scheme을 그대로 따릅니다.
            var request =
                    rest.method(HttpMethod.valueOf(method))
                            .uri(URI.create("http://" + service + path))
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + token());
            if (body != null)
                request.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsBytes(body));
            byte[] response =
                    request.exchange(
                            (req, res) -> {
                                if (res.getStatusCode().value() != 200) throw unavailable();
                                return res.getBody().readAllBytes();
                            });
            var json = mapper.readTree(response);
            if (!json.path("success").isBoolean() || !json.path("success").asBoolean())
                throw unavailable();
            if (!"DELETE".equals(method) && !json.path("data").isObject()) throw unavailable();
            return json.path("data");
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private synchronized String token() {
        if (cached != null && clock.instant().isBefore(expiresAt.minusSeconds(30))) return cached;
        var token =
                tokens.issueService(
                        UUID.fromString(subject),
                        "account-service",
                        Set.of("account.withdrawal"),
                        Duration.ofMinutes(5));
        cached = token.accessToken();
        expiresAt = token.expiresAt();
        return cached;
    }

    private AccountException unavailable() {
        return new AccountException(AccountErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE);
    }
}
