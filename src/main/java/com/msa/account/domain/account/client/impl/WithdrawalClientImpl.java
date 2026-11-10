package com.msa.account.domain.account.client.impl;

import com.msa.account.domain.account.client.WithdrawalClient;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.auth.command.service.AccessTokenService;

import org.springframework.beans.factory.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;

import tools.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

/** Account는 자신이 보유한 기존 issuer로 제한된 서비스 JWT를 발급합니다. 새 로그인이나 개인키 복사는 없습니다. */
@Component
public class WithdrawalClientImpl implements WithdrawalClient {
    private final HttpClient http;
    private final LoadBalancerClient balancer;
    private final AccessTokenService tokens;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String subject;
    private final boolean enabled;
    private String cached;
    private Instant expiresAt = Instant.MIN;

    public WithdrawalClientImpl(
            @Qualifier("withdrawalHttp") HttpClient http,
            LoadBalancerClient balancer,
            AccessTokenService tokens,
            ObjectMapper mapper,
            Clock clock,
            @Value("${ACCOUNT_WITHDRAWAL_SUBJECT:}") String subject,
            @Value("${account.withdrawal.enabled:false}") boolean enabled) {
        this.http = http;
        this.balancer = balancer;
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
            var instance = balancer.choose(service);
            if (instance == null) throw unavailable();
            URI uri = instance.getUri().resolve(path);
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) throw unavailable();
            var builder =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(5))
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + token());
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(
                                    mapper.writeValueAsBytes(body)));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw unavailable();
            var json = mapper.readTree(response.body());
            if (!json.path("success").isBoolean() || !json.path("success").asBoolean())
                throw unavailable();
            if (!"DELETE".equals(method) && !json.path("data").isObject()) throw unavailable();
            return json.path("data");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
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
