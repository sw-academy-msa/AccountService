package com.msa.account.domain.account.client.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.account.domain.account.exception.AccountException;
import com.msa.account.domain.auth.command.dto.response.ServiceTokenResponse;
import com.msa.account.domain.auth.command.service.AccessTokenService;
import com.msa.account.global.config.withdrawal.WithdrawalConfig;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** 탈퇴 확인 호출이 고정 주소 없이 서비스 ID로 나가고, LoadBalancer가 고른 인스턴스에 서비스 JWT를 전달하는지 실제 HTTP fixture로 확인합니다. */
class WithdrawalClientImplTest {
    final List<HttpServer> servers = new ArrayList<>();
    final AtomicReference<String> authorization = new AtomicReference<>();
    LoadBalancerClient balancer;
    WithdrawalClientImpl client;

    @BeforeEach
    void setup() throws Exception {
        balancer = mock(LoadBalancerClient.class);
        when(balancer.execute(anyString(), any()))
                .thenAnswer(
                        invocation -> {
                            var chosen = balancer.choose(invocation.getArgument(0));
                            // 실제 BlockingLoadBalancerClient와 같은 형태로 인스턴스 없음을 재현합니다.
                            if (chosen == null)
                                throw new IllegalStateException("No instances available");
                            return ((LoadBalancerRequest<?>) invocation.getArgument(1))
                                    .apply(chosen);
                        });
        when(balancer.reconstructURI(any(), any()))
                .thenAnswer(
                        invocation ->
                                LoadBalancerUriTools.reconstructURI(
                                        invocation.getArgument(0), invocation.getArgument(1)));
        var tokens = mock(AccessTokenService.class);
        when(tokens.issueService(any(), any(), any(), any()))
                .thenReturn(
                        new ServiceTokenResponse(
                                "synthetic-service-token",
                                "Bearer",
                                300,
                                Instant.now().plusSeconds(300),
                                "account.withdrawal"));
        client =
                new WithdrawalClientImpl(
                        RestClient.builder()
                                .requestFactory(WithdrawalConfig.requestFactory())
                                .requestInterceptor(new LoadBalancerInterceptor(balancer))
                                .build(),
                        tokens,
                        JsonMapper.builder().build(),
                        Clock.systemUTC(),
                        UUID.randomUUID().toString(),
                        true);
    }

    @AfterEach
    void stop() {
        servers.forEach(s -> s.stop(0));
    }

    @Test
    void sendsServiceTokenToChosenInstanceAndSwitchesOnNextRequest() {
        when(balancer.choose("order-command-service")).thenReturn(instance(start("replica-a")));
        var path = "/internal/v1/account-withdrawals/" + UUID.randomUUID();
        assertThat(client.exchange("order-command-service", "GET", path, null).path("replica").asText())
                .isEqualTo("replica-a");
        assertThat(authorization.get()).isEqualTo("Bearer synthetic-service-token");
        // 첫 인스턴스가 목록에서 빠지면 재시작 없이 다음 요청부터 복제본으로 갑니다.
        when(balancer.choose("order-command-service")).thenReturn(instance(start("replica-b")));
        assertThat(client.exchange("order-command-service", "GET", path, null).path("replica").asText())
                .isEqualTo("replica-b");
    }

    @Test
    void missingInstanceBlocksWithdrawalCheckWithoutFixedAddressFallback() {
        when(balancer.choose("payment-service")).thenReturn(null);
        assertThatThrownBy(
                        () ->
                                client.exchange(
                                        "payment-service",
                                        "POST",
                                        "/internal/v1/account-withdrawals/payments",
                                        Map.of("accountId", UUID.randomUUID())))
                .isInstanceOf(AccountException.class);
    }

    private ServiceInstance instance(String address) {
        var uri = java.net.URI.create(address);
        return new DefaultServiceInstance(
                "fixture", "order-command-service", uri.getHost(), uri.getPort(), false);
    }

    private String start(String name) {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    e -> {
                        authorization.set(e.getRequestHeaders().getFirst("Authorization"));
                        byte[] b =
                                ("{\"success\":true,\"data\":{\"replica\":\"" + name + "\"}}")
                                        .getBytes(StandardCharsets.UTF_8);
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, b.length);
                        e.getResponseBody().write(b);
                        e.close();
                    });
            server.start();
            servers.add(server);
            return "http://127.0.0.1:" + server.getAddress().getPort();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
