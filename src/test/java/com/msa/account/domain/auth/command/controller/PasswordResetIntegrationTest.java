package com.msa.account.domain.auth.command.controller;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.verification.repository.*;
import com.msa.account.domain.verification.command.service.*;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import com.msa.account.global.config.security.AuthProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 DB와 공개 API를 사용하며 메일 작업·시계만 대체합니다. 실제 메일을 보내지 않습니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.profiles.active=test", "account.email-verification.max-attempts=3",
        "account.email-verification.max-requests-per-hour=3", "account.auth.max-login-failures=3"})
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
    private static final String EMAIL = "buyer@example.com";
    private static final String OLD = "Password!123";
    private static final String NEW = "Changed!456";
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordResetVerificationRepository resets;
    @Autowired EmailVerificationRepository signups;
    @Autowired RefreshTokenRepository tokens;
    @Autowired LoginAttemptRepository attempts;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthProperties properties;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuthSessionRepository sessions;
    @MockitoBean PasswordResetMailDispatcher dispatcher;
    @MockitoBean VerificationMailSender sender;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final Map<String, String> signupCodes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> eligible = new ConcurrentHashMap<>();
    private Account buyer;

    @BeforeEach
    // 테스트마다 회원·인증 기록·세션을 초기화하고 실제 네트워크 전송 대신 코드만 캡처합니다.
    void setUp() {
        tokens.deleteAll(); sessions.deleteAll(); attempts.deleteAll(); resets.deleteAll(); signups.deleteAll(); accounts.deleteAll();
        now.set(Instant.parse("2026-10-24T00:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get());
        codes.clear(); signupCodes.clear(); eligible.clear();
        doAnswer(call -> { codes.put(call.getArgument(0), call.getArgument(1)); eligible.put(call.getArgument(0), call.getArgument(3)); return null; })
                .when(dispatcher).dispatchAfterCommit(anyString(), anyString(), any(Instant.class), anyBoolean());
        doAnswer(call -> { signupCodes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(sender).send(anyString(), anyString(), any(Instant.class));
        buyer = Account.registerBuyer(EMAIL, encoder.encode(OLD), "김서윤");
        buyer.initializeSignup(now.get(), Set.of(), false, false, false, now.get());
        buyer = accounts.saveAndFlush(buyer);
    }

    @Test
    // 새 비밀번호 저장·증명 소비·모든 기기 세션 폐기가 함께 완료되고 자동 로그인하지 않습니다.
    void resetsPasswordAndRevokesEveryRefreshSession() throws Exception {
        Csrf csrf = csrf();
        Cookie first = login(csrf, OLD).andExpect(status().isOk()).andReturn().getResponse().getCookie(properties.refreshCookieName());
        var secondResponse = login(csrf, OLD).andExpect(status().isOk()).andReturn().getResponse();
        Cookie second = secondResponse.getCookie(properties.refreshCookieName());
        String oldAccess = JsonPath.read(secondResponse.getContentAsString(), "$.accessToken");
        String proof = proof(EMAIL);
        complete(EMAIL, proof, NEW).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(properties.refreshCookieName(), 0));
        assertThat(encoder.matches(NEW, accounts.findById(buyer.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from account.auth_sessions where revoked_at is not null", Long.class)).isEqualTo(2);
        assertThat(resets.findById(EMAIL).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(resets.findById(EMAIL).orElseThrow().getProofHash()).isNotEqualTo(proof);
        assertThat(resets.findById(EMAIL).orElseThrow().getCodeHash()).isNotEqualTo(codes.get(EMAIL));
        refresh(csrf, first).andExpect(status().isUnauthorized());
        refresh(csrf, second).andExpect(status().isUnauthorized());
        login(csrf, OLD).andExpect(status().isUnauthorized());
        login(csrf, NEW).andExpect(status().isOk());
        // 무상태 Access JWT는 기존 정책대로 만료 전까지 유효합니다.
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + oldAccess)).andExpect(status().isOk());
    }

    @Test
    // 미가입 주소에도 같은 응답과 제한을 적용하지만 올바른 형식의 번호를 알아도 증명을 발급하지 않습니다.
    void doesNotExposeAccountExistenceInResponses() throws Exception {
        String known = request(EMAIL);
        String unknown = request("missing@example.com");
        assertThat(known).isNotEqualTo(unknown);
        assertThat(eligible.get(EMAIL)).isTrue();
        assertThat(eligible.get("missing@example.com")).isFalse();
        confirm("missing@example.com", unknown, codes.get("missing@example.com"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_INVALID_CODE"));
        assertThat(resets.findById("missing@example.com").orElseThrow().getProofHash()).isNull();
    }

    @Test
    // 번호 요청만으로 비밀번호나 로그인 세션을 바꾸지 않아 타인의 요청으로 사용자를 차단하지 않습니다.
    void requestAloneDoesNotChangePasswordOrSessions() throws Exception {
        Csrf csrf = csrf();
        Cookie refresh = login(csrf, OLD).andExpect(status().isOk()).andReturn().getResponse().getCookie(properties.refreshCookieName());
        request(EMAIL);
        assertThat(encoder.matches(OLD, accounts.findById(buyer.getId()).orElseThrow().getPasswordHash())).isTrue();
        refresh(csrf, refresh).andExpect(status().isOk());
    }

    @Test
    // 가입용 코드·증명은 재설정에, 재설정용 코드·증명은 가입에 사용할 수 없습니다.
    void isolatesSignupAndPasswordResetCredentials() throws Exception {
        String signupResponse = post("/api/v1/auth/email-verifications", "{\"email\":\"fresh@example.com\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String signupId = JsonPath.read(signupResponse, "$.challengeId");
        confirm("fresh@example.com", signupId, signupCodes.get("fresh@example.com"))
                .andExpect(status().isBadRequest());
        String signupProofResponse = post("/api/v1/auth/email-verifications/confirm", confirmBody("fresh@example.com", signupId, signupCodes.get("fresh@example.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        complete("fresh@example.com", JsonPath.read(signupProofResponse, "$.verificationToken"), NEW)
                .andExpect(status().isBadRequest());
        String resetId = request(EMAIL);
        post("/api/v1/auth/email-verifications/confirm", confirmBody(EMAIL, resetId, codes.get(EMAIL)))
                .andExpect(status().isBadRequest());
        String resetProof = confirmedProof(EMAIL, resetId);
        post("/api/v1/auth/signup", "{\"email\":\"" + EMAIL + "\",\"password\":\"" + NEW
                + "\",\"name\":\"김서윤\",\"verificationToken\":\"" + resetProof + "\"}")
                .andExpect(status().isBadRequest());
        complete(EMAIL, resetProof, NEW).andExpect(status().isNoContent());
    }

    @Test
    // 다른 이메일로 증명을 제출하면 거절하고 원래 이메일의 증명은 소비하지 않습니다.
    void bindsProofToEmail() throws Exception {
        String proof = proof(EMAIL);
        complete("other@example.com", proof, NEW).andExpect(status().isBadRequest());
        assertThat(resets.findById(EMAIL).orElseThrow().getConsumedAt()).isNull();
        complete(" Buyer@Example.COM ", proof, NEW).andExpect(status().isNoContent());
    }

    @Test
    // 이미 성공한 증명을 재사용해서 다시 비밀번호를 바꿀 수 없습니다.
    void consumesProofOnce() throws Exception {
        String proof = proof(EMAIL);
        complete(EMAIL, proof, NEW).andExpect(status().isNoContent());
        complete(EMAIL, proof, "Another!789").andExpect(status().isBadRequest());
        assertThat(encoder.matches(NEW, accounts.findById(buyer.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    // 서버 시계가 만료 경계에 도달한 번호와 증명은 사용할 수 없습니다.
    void expiresCodeAndProof() throws Exception {
        String id = request(EMAIL);
        now.updateAndGet(time -> time.plusSeconds(300));
        confirm(EMAIL, id, codes.get(EMAIL)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        String proof = proof(EMAIL);
        now.updateAndGet(time -> time.plusSeconds(900));
        complete(EMAIL, proof, NEW).andExpect(status().isBadRequest());
    }

    @Test
    // 오입력 제한은 실패 응답에도 영속 저장됩니다.
    void persistsWrongCodeAttempts() throws Exception {
        String id = request(EMAIL);
        String wrong = "000000".equals(codes.get(EMAIL)) ? "111111" : "000000";
        for (int i = 0; i < 3; i++) confirm(EMAIL, id, wrong).andExpect(status().isBadRequest());
        assertThat(resets.findById(EMAIL).orElseThrow().getAttempts()).isEqualTo(3);
        confirm(EMAIL, id, codes.get(EMAIL)).andExpect(status().isTooManyRequests());
    }

    @Test
    // 재발급하면 이전 요청 ID와 이미 받은 재설정 증명을 모두 무효화합니다.
    void resendInvalidatesPreviousCodeAndProof() throws Exception {
        String oldId = request(EMAIL);
        String proof = confirmedProof(EMAIL, oldId);
        now.updateAndGet(time -> time.plusSeconds(60));
        String nextId = request(EMAIL);
        confirm(EMAIL, oldId, codes.get(EMAIL)).andExpect(status().isBadRequest());
        complete(EMAIL, proof, NEW).andExpect(status().isBadRequest());
        complete(EMAIL, confirmedProof(EMAIL, nextId), NEW).andExpect(status().isNoContent());
    }

    @Test
    // 미가입 주소에도 동일한 재전송 간격과 시간당 발급 한도를 적용합니다.
    void rateLimitsKnownAndUnknownEmail() throws Exception {
        for (String email : List.of(EMAIL, "missing@example.com")) {
            request(email);
            post("/api/v1/auth/password-resets", "{\"email\":\"" + email + "\"}").andExpect(status().isTooManyRequests());
            for (int i = 0; i < 2; i++) { now.updateAndGet(time -> time.plusSeconds(60)); request(email); }
            now.updateAndGet(time -> time.plusSeconds(60));
            post("/api/v1/auth/password-resets", "{\"email\":\"" + email + "\"}").andExpect(status().isTooManyRequests());
        }
    }

    @Test
    // 새 비밀번호 검증이 실패하면 증명을 소비하거나 기존 비밀번호를 변경하지 않습니다.
    void validatesPasswordBeforeConsumingProof() throws Exception {
        String proof = proof(EMAIL);
        complete(EMAIL, proof, "short").andExpect(status().isBadRequest());
        complete(EMAIL, proof, "가".repeat(30) + "A1!").andExpect(status().isBadRequest());
        assertThat(resets.findById(EMAIL).orElseThrow().getConsumedAt()).isNull();
        assertThat(encoder.matches(OLD, accounts.findById(buyer.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    // 인증 후 정지된 계정은 변경할 수 없고 증명 소비도 롤백합니다.
    void checksLatestAccountStateAtCompletion() throws Exception {
        String proof = proof(EMAIL);
        jdbc.update("update account.accounts set status = 'SUSPENDED' where id = ?", buyer.getId());
        complete(EMAIL, proof, NEW).andExpect(status().isBadRequest());
        assertThat(resets.findById(EMAIL).orElseThrow().getConsumedAt()).isNull();
    }

    @Test
    // 같은 증명으로 동시에 변경해도 한 요청만 성공합니다.
    void serializesConcurrentCompletion() throws Exception {
        String proof = proof(EMAIL);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> complete(EMAIL, proof, NEW).andReturn().getResponse().getStatus());
            var b = executor.submit(() -> complete(EMAIL, proof, "Another!789").andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 400);
        }
    }

    @Test
    // 세션 폐기가 실패하면 비밀번호 변경과 증명 소비까지 전부 롤백되어 재시도 가능합니다.
    void rollsBackPasswordProofAndRevocationTogether() throws Exception {
        Csrf csrf = csrf();
        Cookie refresh = login(csrf, OLD).andExpect(status().isOk()).andReturn().getResponse().getCookie(properties.refreshCookieName());
        String proof = proof(EMAIL);
        doThrow(new DataIntegrityViolationException("test revoke failure")).when(sessions).revokeAllByAccountId(any(UUID.class), any(Instant.class));
        complete(EMAIL, proof, NEW).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_DATA_CONFLICT"));
        assertThat(resets.findById(EMAIL).orElseThrow().getConsumedAt()).isNull();
        assertThat(encoder.matches(OLD, accounts.findById(buyer.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from account.auth_sessions where revoked_at is null", Long.class)).isEqualTo(1);
        reset(sessions);
        complete(EMAIL, proof, NEW).andExpect(status().isNoContent());
        refresh(csrf, refresh).andExpect(status().isUnauthorized());
    }

    @Test
    // 로그인 JWT나 Refresh Token은 재설정 전용 증명으로 사용할 수 없습니다.
    void rejectsLoginTokensAsResetProof() throws Exception {
        var response = login(csrf(), OLD).andExpect(status().isOk()).andReturn().getResponse();
        String jwt = JsonPath.read(response.getContentAsString(), "$.accessToken");
        complete(EMAIL, jwt, NEW).andExpect(status().isBadRequest());
        complete(EMAIL, response.getCookie(properties.refreshCookieName()).getValue(), NEW).andExpect(status().isBadRequest());
    }

    @Test
    // 올바른 이메일 복구를 마치면 이전 로그인 실패 제한도 초기화됩니다.
    void clearsLoginFailureLimitAfterVerifiedRecovery() throws Exception {
        Csrf csrf = csrf();
        for (int i = 0; i < properties.maxLoginFailures(); i++) login(csrf, "Wrong!123").andExpect(status().isUnauthorized());
        login(csrf, OLD).andExpect(status().isTooManyRequests());
        complete(EMAIL, proof(EMAIL), NEW).andExpect(status().isNoContent());
        login(csrf, NEW).andExpect(status().isOk());
    }

    // 응답에는 실제 가입 여부·인증번호·계정 ID가 포함되지 않습니다.
    private String request(String email) throws Exception {
        var response = post("/api/v1/auth/password-resets", "{\"email\":\"" + email + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.accountId").doesNotExist()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.challengeId");
    }
    private String proof(String email) throws Exception { return confirmedProof(email, request(email)); }
    private String confirmedProof(String email, String id) throws Exception {
        var response = confirm(email, id, codes.get(email)).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").doesNotExist()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.resetToken");
    }
    private ResultActions confirm(String email, String id, String code) throws Exception {
        return post("/api/v1/auth/password-resets/confirm", confirmBody(email, id, code));
    }
    private String confirmBody(String email, String id, String code) {
        return "{\"email\":\"" + email + "\",\"challengeId\":\"" + id + "\",\"code\":\"" + code + "\"}";
    }
    private ResultActions complete(String email, String proof, String password) throws Exception {
        return post("/api/v1/auth/password-resets/complete", "{\"email\":\"" + email + "\",\"resetToken\":\"" + proof + "\",\"newPassword\":\"" + password + "\"}");
    }
    private ResultActions post(String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }
    private Csrf csrf() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        return new Csrf(response.getCookie(properties.csrfCookieName()), JsonPath.read(response.getContentAsString(), "$.token"));
    }
    private ResultActions login(Csrf csrf, String password) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"));
    }
    private ResultActions refresh(Csrf csrf, Cookie cookie) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                .cookie(csrf.cookie(), cookie).header("X-XSRF-TOKEN", csrf.token()));
    }
    private record Csrf(Cookie cookie, String token) { }
}
