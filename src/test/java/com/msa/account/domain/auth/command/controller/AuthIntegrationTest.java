package com.msa.account.domain.auth.command.controller;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.global.config.security.AuthProperties;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.auth.command.service.AuthCleanupService;
import com.msa.account.domain.auth.entity.AuthSession;
import com.msa.account.domain.auth.entity.RefreshToken;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 쿠키·CSRF·JWT와 실제 PostgreSQL의 세션·토큰 상태를 통합 검증합니다. 외부 API를 호출하지 않습니다. */
@Import({TestcontainersConfiguration.class, AuthIntegrationTest.RoleProbeController.class})
@SpringBootTest(properties = {"spring.profiles.active=test", "account.auth.allowed-origins=http://localhost:5173",
        "account.auth.cleanup.batch-size=2"})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @MockitoSpyBean AuthSessionRepository sessions;
    @Autowired AuthCleanupService cleanup;
    @Autowired RefreshTokenRepository tokens;
    @Autowired LoginAttemptRepository attempts;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthProperties properties;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private Account buyer;

    @BeforeEach
    // 계정 삭제 전에 토큰과 세션을 정리하며 모든 시간 검증에 같은 서버 시계를 사용합니다.
    void setUp() {
        tokens.deleteAll(); sessions.deleteAll(); attempts.deleteAll(); accounts.deleteAll();
        now.set(Instant.parse("2026-10-24T00:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get());
        buyer = Account.registerBuyer("buyer@example.com", passwords.encode("Password!123"), "김서윤");
        buyer.initializeSignup(now.get(), Set.of(), false, false, false, now.get());
        buyer = accounts.saveAndFlush(buyer);
    }

    @Test
    // 실제 GET CSRF 준비 후 로그인하고, 토큰에는 개인정보 없이 UUID와 구매자 권한만 포함합니다.
    void loginReturnsJwtAndHttpOnlyRefreshCookie() throws Exception {
        Login login = login(csrf());
        assertThat(login.refresh().isHttpOnly()).isTrue();
        assertThat(login.refresh().getPath()).isEqualTo("/api/v1/auth");
        Jwt jwt = decoder.decode(login.access());
        assertThat(jwt.getSubject()).isEqualTo(buyer.getId().toString());
        assertThat(jwt.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(jwt.getAudience()).contains(properties.audience());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("BUYER");
        assertThat(jwt.getClaims()).doesNotContainKeys("email", "name", "password");
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(tokens.existsById(login.refresh().getValue())).isFalse();
        assertThat(jdbc.queryForObject("select length(hash) from account.refresh_tokens", Integer.class)).isEqualTo(64);
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + login.access())
                        .param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(buyer.getId().toString()));
    }

    @Test
    // 쿠키를 자동 전송하는 엔드포인트는 유효한 CSRF 헤더 없이 실행되지 않습니다.
    void requiresCsrfForCookieEndpoints() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON_CSRF_REJECTED"));
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
        Csrf csrf = csrf();
        mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.header(), "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isForbidden());
        assertThat(sessions.count()).isZero();
    }

    @Test
    // 미가입 이메일과 잘못된 비밀번호는 같은 오류이며 인증하지 않은 개발 계정은 로그인할 수 없습니다.
    void rejectsBadCredentialsAndUnverifiedAccount() throws Exception {
        Csrf csrf = csrf();
        postLogin(csrf, credentials().replace("Password!123", "Wrong!123"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        postLogin(csrf, credentials().replace("buyer@example.com", "missing@example.com"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        accounts.saveAndFlush(Account.registerBuyer("legacy@example.com", passwords.encode("Password!123"), "개발계정"));
        postLogin(csrf, credentials().replace("buyer@example.com", "legacy@example.com"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_UNAVAILABLE"));
    }

    @Test
    // 정지된 회원은 정상 비밀번호라도 새 로그인 세션을 만들 수 없습니다.
    void rejectsSuspendedAccount() throws Exception {
        jdbc.update("update account.accounts set status = 'SUSPENDED' where id = ?", buyer.getId());
        postLogin(csrf(), credentials()).andExpect(status().isForbidden());
    }

    @Test
    // 만료 후 7일 이내의 기록은 유지하고, 정확히 7일이 지난 경계부터 정리합니다.
    void deletesOnlyAfterSevenDaysOfRetention() {
        UUID id = storeSession(now.get().minus(Duration.ofDays(6)), false);
        assertThat(cleanup.deleteExpiredBatch()).isZero();
        assertThat(sessions.existsById(id)).isTrue();
        now.updateAndGet(time -> time.plus(Duration.ofDays(1)));
        assertThat(cleanup.deleteExpiredBatch()).isEqualTo(1);
        assertThat(sessions.existsById(id)).isFalse();
        assertThat(tokens.count()).isZero();
        assertThat(accounts.existsById(buyer.getId())).isTrue();
        assertThat(cleanup.deleteExpiredBatch()).isZero();
    }

    @Test
    // 활성 세션과 만료 기준이 지나지 않은 로그아웃 세션은 정리 대상이 아닙니다.
    void preservesActiveAndRecentlyExpiredSessions() {
        UUID old = storeSession(now.get().minus(Duration.ofDays(8)), true);
        UUID recent = storeSession(now.get().minus(Duration.ofDays(6)), false);
        UUID active = storeSession(now.get().plus(Duration.ofDays(1)), false);
        UUID revoked = storeSession(now.get().plus(Duration.ofDays(2)), true);
        assertThat(cleanup.deleteExpiredBatch()).isEqualTo(1);
        assertThat(sessions.existsById(old)).isFalse();
        assertThat(sessions.existsById(recent)).isTrue();
        assertThat(sessions.existsById(active)).isTrue();
        assertThat(sessions.existsById(revoked)).isTrue();
        assertThat(tokens.count()).isEqualTo(3);
    }

    @Test
    // 복제 인스턴스처럼 동시에 배치를 실행해도 중복 삭제 없이 설정한 크기만 처리합니다.
    void boundsAndDistributesConcurrentCleanupBatches() throws Exception {
        for (int i = 0; i < 5; i++) storeSession(now.get().minus(Duration.ofDays(8)), false);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cleanup.deleteExpiredBatch());
            var second = executor.submit(() -> cleanup.deleteExpiredBatch());
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(cleanup.deleteExpiredBatch()).isEqualTo(1);
        assertThat(tokens.count()).isZero();
    }

    @Test
    // 세션 삭제가 실패하면 먼저 지운 토큰도 롤백되어 불완전한 정리 상태가 남지 않습니다.
    void rollsBackTokenDeletionWhenSessionDeletionFails() {
        UUID id = storeSession(now.get().minus(Duration.ofDays(8)), false);
        doThrow(new DataIntegrityViolationException("test cleanup failure")).when(sessions).deleteCleanupBatch(anyList());
        assertThatThrownBy(() -> cleanup.deleteExpiredBatch()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sessions.existsById(id)).isTrue();
        assertThat(tokens.count()).isEqualTo(1);
        reset(sessions);
        assertThat(cleanup.deleteExpiredBatch()).isEqualTo(1);
    }

    // 원문 Refresh Token 대신 가짜 해시만 넣어 정리 대상 데이터를 만듭니다.
    private UUID storeSession(Instant expiresAt, boolean revoked) {
        var session = new AuthSession(buyer, expiresAt.minus(properties.refreshTtl()), expiresAt);
        if (revoked) session.revoke(now.get());
        session = sessions.saveAndFlush(session);
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        tokens.saveAndFlush(new RefreshToken(hash, session, expiresAt.minus(properties.refreshTtl())));
        return session.getId();
    }

    @Test
    // DB에 기록된 실패 한도를 넘으면 올바른 비밀번호도 시간 창이 끝날 때까지 거절합니다.
    void limitsRepeatedLoginFailures() throws Exception {
        Csrf csrf = csrf();
        for (int i = 0; i < properties.maxLoginFailures(); i++) {
            postLogin(csrf, credentials().replace("Password!123", "Wrong!123"))
                    .andExpect(status().isUnauthorized());
        }
        postLogin(csrf, credentials()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_RATE_LIMITED"));
        now.updateAndGet(time -> time.plus(properties.loginWindow()));
        postLogin(csrf, credentials()).andExpect(status().isOk());
        assertThat(attempts.existsById("buyer@example.com")).isFalse();
    }

    @Test
    // 길이 검증이 BCrypt 예외보다 먼저 동작하므로 멀티바이트 과다 입력도 400으로 응답합니다.
    void rejectsOversizedUtf8Password() throws Exception {
        postLogin(csrf(), credentials().replace("Password!123", "가".repeat(30) + "A1!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    // roles를 ROLE_ 권한으로 연결하고 메서드 권한 거절은 500이 아닌 403으로 응답합니다.
    void enforcesRoleBasedAuthorization() throws Exception {
        Login login = login(csrf());
        mvc.perform(get("/api/v1/test/admin").header("Authorization", "Bearer " + login.access()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
        var claims = JwtClaimsSet.builder().subject(buyer.getId().toString()).issuer(properties.issuer())
                .audience(List.of(properties.audience())).issuedAt(now.get()).expiresAt(now.get().plusSeconds(600))
                .claim("token_use", "access").claim("roles", List.of("ADMIN")).build();
        String admin = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        mvc.perform(get("/api/v1/test/admin").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    }

    /** 권한 변환 검증용 컨트롤러이며 운영 소스에는 포함되지 않습니다. */
    @RestController
    public static class RoleProbeController {
        @GetMapping("/api/v1/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, Boolean> admin() { return Map.of("allowed", true); }
    }

    @Test
    // 새로 발급받은 토큰은 기존 토큰을 대체하고, 기존 토큰 재사용은 새 토큰까지 폐기합니다.
    void rotatesRefreshAndDetectsReuse() throws Exception {
        Csrf csrf = csrf();
        Login login = login(csrf);
        var response = refresh(csrf, login.refresh()).andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn().getResponse();
        Cookie replacement = response.getCookie(properties.refreshCookieName());
        assertThat(replacement.getValue()).isNotEqualTo(login.refresh().getValue());
        refresh(csrf, login.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSED"));
        refresh(csrf, replacement).andExpect(status().isUnauthorized());
    }

    @Test
    // 동시 재발급은 하나만 성공하며 두 번째 사용을 감지해 세션을 안전하게 종료합니다.
    void serializesConcurrentRefreshRequests() throws Exception {
        Csrf csrf = csrf();
        Login login = login(csrf);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> request = () -> refresh(csrf, login.refresh()).andReturn().getResponse().getStatus();
            var a = executor.submit(request); var b = executor.submit(request);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbc.queryForObject("select revoked_at is not null from account.auth_sessions", Boolean.class)).isTrue();
    }

    @Test
    // 로그아웃은 현재 세션을 폐기하고 반복 호출도 성공합니다. Access JWT는 만료 전까지 남습니다.
    void logoutRevokesRefreshButNotAlreadyIssuedAccessJwt() throws Exception {
        Csrf csrf = csrf();
        Login login = login(csrf);
        mvc.perform(post("/api/v1/auth/logout").cookie(csrf.cookie(), login.refresh()).header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(properties.refreshCookieName(), 0));
        mvc.perform(post("/api/v1/auth/logout").cookie(csrf.cookie()).header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent());
        refresh(csrf, login.refresh()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + login.access()))
                .andExpect(status().isOk());
    }

    @Test
    // 한 기기의 로그아웃이 다른 기기의 로그인 세션까지 폐기하지는 않습니다.
    void keepsOtherLoginSessionActive() throws Exception {
        Csrf csrf = csrf();
        Login a = login(csrf); Login b = login(csrf);
        mvc.perform(post("/api/v1/auth/logout").cookie(csrf.cookie(), a.refresh()).header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent());
        refresh(csrf, b.refresh()).andExpect(status().isOk());
    }

    @Test
    // 유효 시간이 지난 JWT와 Refresh Token은 서버 시계 기준으로 거절합니다.
    void rejectsExpiredTokens() throws Exception {
        Csrf csrf = csrf();
        Login login = login(csrf);
        now.updateAndGet(time -> time.plus(properties.accessTtl()).plusSeconds(31));
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + login.access()))
                .andExpect(status().isUnauthorized());
        now.updateAndGet(time -> time.plus(properties.refreshTtl()));
        refresh(csrf, login.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    // JWT 검증은 서명뿐 아니라 발급자·대상·토큰 용도도 확인합니다.
    void rejectsWrongIssuerAudienceAndUse() throws Exception {
        for (String invalid : List.of("issuer", "audience", "use")) {
            var claims = JwtClaimsSet.builder().subject(buyer.getId().toString()).issuedAt(now.get())
                    .expiresAt(now.get().plusSeconds(600)).issuer(invalid.equals("issuer") ? "other" : properties.issuer())
                    .audience(List.of(invalid.equals("audience") ? "other" : properties.audience()))
                    .claim("token_use", invalid.equals("use") ? "refresh" : "access").build();
            String jwt = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
            mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    // 공개키만 받은 별도 검증기도 같은 JWT를 검증할 수 있고 개인키 파라미터는 제공하지 않습니다.
    void exposesPublicOnlyJwksForOtherServices() throws Exception {
        Login login = login(csrf());
        String json = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].n").exists()).andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist()).andReturn().getResponse().getContentAsString();
        var key = JWKSet.parse(json).getKeys().getFirst().toRSAKey();
        assertThat(key.isPrivate()).isFalse();
        var external = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        // 시계가 고정된 테스트이므로 시간 검사기도 같은 시계를 사용합니다.
        var timestamp = new JwtTimestampValidator(); timestamp.setClock(clock);
        external.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.issuer()), new JwtClaimValidator<List<String>>("aud", audience -> audience.contains(properties.audience()))));
        assertThat(external.decode(login.access()).getSubject()).isEqualTo(buyer.getId().toString());
    }

    @Test
    // 위조된 문자열·JWT 없는 본인 조회·허용하지 않은 Origin은 거절합니다.
    void rejectsInvalidAccessAndDisallowedOrigin() throws Exception {
        mvc.perform(get("/api/v1/accounts/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options("/api/v1/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/login").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // 브라우저처럼 CSRF 쿠키와 JSON의 헤더 토큰을 함께 보관합니다.
    private Csrf csrf() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        return new Csrf(response.getCookie(properties.csrfCookieName()), JsonPath.read(response.getContentAsString(), "$.headerName"),
                JsonPath.read(response.getContentAsString(), "$.token"));
    }
    private Login login(Csrf csrf) throws Exception {
        var response = postLogin(csrf, credentials()).andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn().getResponse();
        return new Login(JsonPath.read(response.getContentAsString(), "$.accessToken"), response.getCookie(properties.refreshCookieName()));
    }
    private ResultActions postLogin(Csrf csrf, String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private ResultActions refresh(Csrf csrf, Cookie refresh) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(csrf.cookie(), refresh).header(csrf.header(), csrf.token()));
    }
    private String credentials() { return "{\"email\":\"buyer@example.com\",\"password\":\"Password!123\"}"; }
    private record Csrf(Cookie cookie, String header, String token) { }
    private record Login(String access, Cookie refresh) { }
}
