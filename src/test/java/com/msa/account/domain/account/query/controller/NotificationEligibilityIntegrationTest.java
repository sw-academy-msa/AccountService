package com.msa.account.domain.account.query.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.account.global.config.database.TestcontainersConfiguration;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 기존 발급 API와 별도 내부 체인을 실제 서명 토큰으로 함께 검증합니다. */
@SpringBootTest(properties = {"spring.profiles.active=test", "account.service-auth.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationEligibilityIntegrationTest {
    static final UUID SUBJECT = UUID.randomUUID();
    static final String SECRET = "synthetic-notification-test-secret";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "account.service-auth.clients.notification-service.subject",
                () -> SUBJECT.toString());
        p.add(
                "account.service-auth.clients.notification-service.secret-hash",
                () -> new BCryptPasswordEncoder(10).encode(SECRET));
        p.add(
                "account.service-auth.clients.notification-service.scopes",
                () -> "account.notifications.read,ai.personalization.read");
        // 기존 Order 기본 클라이언트도 활성화된 계약에 맞는 합성 값만 제공합니다.
        p.add(
                "account.service-auth.clients.order-command-service.subject",
                () -> "11111111-1111-1111-1111-111111111111");
        p.add(
                "account.service-auth.clients.order-command-service.secret-hash",
                () -> new BCryptPasswordEncoder(10).encode("synthetic-order-secret"));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RSAKey key;

    String issue() throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        post("/internal/v1/auth/service-tokens")
                                                .with(httpBasic("notification-service", SECRET))
                                                .contentType("application/x-www-form-urlencoded")
                                                .param("grant_type", "client_credentials")
                                                .param(
                                                        "scope",
                                                        "account.notifications.read"
                                                                + " ai.personalization.read"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("access_token")
                .asText();
    }

    @Test
    void existingIssuerProvidesCombinedScopeAndMissingAccountIsIneligible() throws Exception {
        mvc.perform(
                        get("/internal/v1/notification-eligibility")
                                .param("accountId", UUID.randomUUID().toString())
                                .header("Authorization", "Bearer " + issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(false));
    }

    @Test
    void serviceCannotEnterExistingUserApi() throws Exception {
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + issue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousAndWrongServiceClaimsCannotEnterPrivateApi() throws Exception {
        mvc.perform(
                        get("/internal/v1/notification-eligibility")
                                .param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        for (String missing : List.of("principal_type", "client_id", "scope", "sub")) {
            var claims =
                    new JWTClaimsSet.Builder()
                            .subject(SUBJECT.toString())
                            .issuer("http://localhost:8080")
                            .audience("stockdrop-api")
                            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                            .claim("token_use", "access")
                            .claim("principal_type", "service")
                            .claim("client_id", "notification-service")
                            .claim("scope", "account.notifications.read");
            claims.claim(missing, null);
            var token =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                            claims.build());
            token.sign(new RSASSASigner(key));
            mvc.perform(
                            get("/internal/v1/notification-eligibility")
                                    .param("accountId", UUID.randomUUID().toString())
                                    .header("Authorization", "Bearer " + token.serialize()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
