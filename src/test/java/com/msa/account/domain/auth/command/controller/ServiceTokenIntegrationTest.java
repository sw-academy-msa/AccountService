package com.msa.account.domain.auth.command.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.auth.command.service.AccessTokenService;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

/** 기존 JWT 키/인코더·실제 BCrypt와 필터 체인을 검증합니다. 사용자 세션/Refresh를 만들지 않습니다. */
@SpringBootTest(properties = {"spring.profiles.active=test", "account.service-auth.enabled=true"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ServiceTokenIntegrationTest {
    static final String CLIENT = "order-command-service";
    static final String SECRET = "synthetic-service-secret-for-test-only";
    static final UUID SUBJECT = UUID.randomUUID();
    static final String HASH = new BCryptPasswordEncoder(12).encode(SECRET);

    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry r) {
        r.add("account.service-auth.enabled", () -> "true");
        r.add(
                "account.service-auth.clients.order-command-service.subject",
                () -> SUBJECT.toString());
        r.add("account.service-auth.clients.order-command-service.secret-hash", () -> HASH);
    }

    @Autowired MockMvc mvc;
    @Autowired RSAKey key;
    @Autowired JwtDecoder decoder;
    @Autowired AccessTokenService tokens;
    @Autowired JsonMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.msa.account.domain.account.repository.AccountRepository accounts;
    @MockitoBean Clock clock;

    @BeforeEach
    void clock() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
    }

    String issue(String scope) throws Exception {
        var result =
                mvc.perform(
                                post("/internal/v1/auth/service-tokens")
                                        .with(httpBasic(CLIENT, SECRET))
                                        .contentType("application/x-www-form-urlencoded")
                                        .param("grant_type", "client_credentials")
                                        .param("scope", scope))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(header().doesNotExist("Set-Cookie"))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString())
                .path("access_token")
                .asText();
    }

    @Test
    void reusesExistingRsaKeyIssuerAudienceAndKeepsRolesEmpty() throws Exception {
        String value = issue("product.stock");
        var jwt = SignedJWT.parse(value);
        assertThat(jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(key.getKeyID());
        var c = jwt.getJWTClaimsSet();
        assertThat(c.getSubject()).isEqualTo(SUBJECT.toString());
        assertThat(c.getAudience()).contains("stockdrop-api");
        assertThat(c.getStringClaim("token_use")).isEqualTo("access");
        assertThat(c.getStringClaim("scope")).isEqualTo("product.stock");
        assertThat(c.getStringClaim("client_id")).isEqualTo(CLIENT);
        assertThat(c.getStringClaim("principal_type")).isEqualTo("service");
        assertThat(c.getStringListClaim("roles")).isEmpty();
        assertThat(c.getExpirationTime().toInstant()).isEqualTo(clock.instant().plusSeconds(300));
        assertThat(c.getClaims())
                .doesNotContainKeys("client_secret", "email", "password", "refresh_token");
        assertThat(jdbc.queryForObject("select count(*) from account.auth_sessions", Long.class))
                .isZero();
    }

    @Test
    void machineTokenCannotBeUsedAsUserOrBasicCredential() throws Exception {
        String value = issue("product.stock");
        assertThatThrownBy(() -> decoder.decode(value))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + value))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/internal/v1/auth/service-tokens")
                                .header("Authorization", "Bearer " + value)
                                .contentType("application/x-www-form-urlencoded")
                                .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnonymousUnknownClientBadSecretAndPrivilegeEscalation() throws Exception {
        mvc.perform(
                        post("/internal/v1/auth/service-tokens")
                                .contentType("application/x-www-form-urlencoded")
                                .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        for (String id : List.of(CLIENT, "unknown-client")) {
            mvc.perform(
                            post("/internal/v1/auth/service-tokens")
                                    .with(httpBasic(id, "wrong-secret"))
                                    .contentType("application/x-www-form-urlencoded")
                                    .param("grant_type", "client_credentials"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(
                        post("/internal/v1/auth/service-tokens")
                                .with(httpBasic(CLIENT, SECRET))
                                .contentType("application/x-www-form-urlencoded")
                                .param("grant_type", "client_credentials")
                                .param("scope", "product.stock admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRefreshGrantDuplicateParametersAndUserClaims() throws Exception {
        for (String grant : List.of("refresh_token", "password", "")) {
            mvc.perform(
                            post("/internal/v1/auth/service-tokens")
                                    .with(httpBasic(CLIENT, SECRET))
                                    .contentType("application/x-www-form-urlencoded")
                                    .param("grant_type", grant))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post("/internal/v1/auth/service-tokens")
                                .with(httpBasic(CLIENT, SECRET))
                                .contentType("application/x-www-form-urlencoded")
                                .param("grant_type", "client_credentials", "password"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/internal/v1/auth/service-tokens")
                                .with(httpBasic(CLIENT, SECRET))
                                .contentType("application/x-www-form-urlencoded")
                                .param("grant_type", "client_credentials")
                                .param("roles", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reacquisitionUsesSameSubjectButNewTokenAndDoesNotChangeUserJwt() throws Exception {
        var first = SignedJWT.parse(issue("product.stock"));
        var nextTime = clock.instant().plusSeconds(250);
        when(clock.instant()).thenReturn(nextTime);
        var next = SignedJWT.parse(issue("product.stock"));
        assertThat(next.getJWTClaimsSet().getSubject())
                .isEqualTo(first.getJWTClaimsSet().getSubject());
        assertThat(next.getJWTClaimsSet().getJWTID())
                .isNotEqualTo(first.getJWTClaimsSet().getJWTID());
        var account = Account.registerBuyer("synthetic@example.test", "not-a-real-hash", "합성 사용자");
        account = accounts.saveAndFlush(account);
        var user = decoder.decode(tokens.issue(account).accessToken());
        assertThat(user.getClaimAsStringList("roles")).containsExactly("BUYER");
        assertThat(user.getClaims()).doesNotContainKey("scope");
    }
}
