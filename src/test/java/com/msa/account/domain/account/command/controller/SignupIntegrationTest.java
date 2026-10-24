package com.msa.account.domain.account.command.controller;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.TestcontainersConfiguration;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.verification.repository.EmailVerificationRepository;
import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.domain.verification.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SMTP와 시계만 대체하고 실제 PostgreSQL로 인증·가입의 경계 조건을 검증합니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.profiles.active=test",
        "account.email-verification.max-attempts=3", "account.email-verification.max-requests-per-hour=3"})
@AutoConfigureMockMvc
class SignupIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private AccountRepository accounts;
    @Autowired private EmailVerificationRepository verifications;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private VerificationMailSender mail;
    @MockitoBean private Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @BeforeEach
    // 테스트 데이터를 제거하고 서버 시각과 수신 메일을 초기화합니다.
    void setUp() {
        accounts.deleteAll();
        verifications.deleteAll();
        codes.clear();
        now.set(Instant.parse("2026-10-23T00:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get());
        doAnswer(call -> { codes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(mail).send(anyString(), anyString(), any(Instant.class));
    }

    @Test
    // 휴대전화·선택 설정 없이 가입하고 비밀번호 해시와 초기 미동의 상태를 저장합니다.
    void signsUpWithVerifiedEmailAndDefaults() throws Exception {
        String proof = verify("buyer@example.com");
        signup(" Buyer@Example.COM ", proof, "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.name").value("김서윤"))
                .andExpect(jsonPath("$.roles[0]").value("BUYER"))
                .andExpect(jsonPath("$.emailVerifiedAt").exists())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
        var account = accounts.findByEmail("buyer@example.com").orElseThrow();
        assertThat(encoder.matches("Password!123", account.getPasswordHash())).isTrue();
        assertThat(account.getPasswordHash()).isNotEqualTo("Password!123");
        assertThat(count("account_interests")).isZero();
        assertThat(jdbc.queryForObject("select marketing_agreed from account.account_consents", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select sale_start or personalized_recommendation from account.account_notification_settings", Boolean.class)).isFalse();
        assertThat(count("account_consent_histories")).isEqualTo(1);
        var verification = verifications.findById("buyer@example.com").orElseThrow();
        assertThat(verification.getConsumedAt()).isNotNull();
        assertThat(verification.getCodeHash()).isNotEqualTo(codes.get("buyer@example.com"));
        assertThat(verification.getProofHash()).isNotEqualTo(proof);
    }

    @Test
    // 선택 정보는 별도 테이블에 저장하고 중복 카테고리는 한 번만 등록합니다.
    void savesPreferencesSeparately() throws Exception {
        signup("buyer@example.com", verify("buyer@example.com"), """
                ,"interestCategories":["FASHION","ELECTRONICS","FASHION"],
                "notificationSettings":{"saleStart":true,"personalizedRecommendation":true},
                "marketingConsent":true
                """).andExpect(status().isCreated());
        assertThat(count("account_interests")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select marketing_agreed from account.account_consents", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select agreed_at is not null from account.account_consents", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select sale_start and personalized_recommendation from account.account_notification_settings", Boolean.class)).isTrue();
        assertThat(count("account_consent_histories")).isEqualTo(1);
    }

    @Test
    // 빈 알림 객체와 명시적인 null을 보내도 수신 동의로 해석하지 않습니다.
    void defaultsExplicitNullAndEmptySettingsToFalse() throws Exception {
        signup("buyer@example.com", verify("buyer@example.com"),
                ",\"notificationSettings\":{},\"marketingConsent\":null,\"interestCategories\":null")
                .andExpect(status().isCreated());
        assertThat(count("account_interests")).isZero();
        assertThat(jdbc.queryForObject("select marketing_agreed from account.account_consents", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select sale_start or personalized_recommendation from account.account_notification_settings", Boolean.class)).isFalse();
    }

    @Test
    // DB 저장 실패 시 증명 소비도 롤백되므로 동일 증명으로 가입을 재시도할 수 있습니다.
    void rollsBackProofConsumptionWhenSavingFails() throws Exception {
        String proof = verify("buyer@example.com");
        doThrow(new DataIntegrityViolationException("test storage conflict")).when(accounts).saveAndFlush(any());
        signup("buyer@example.com", proof, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_DATA_CONFLICT"));
        assertThat(verifications.findById("buyer@example.com").orElseThrow().getConsumedAt()).isNull();
        assertThat(count("account_consents")).isZero();
        reset(accounts);
        signup("buyer@example.com", proof, "").andExpect(status().isCreated());
    }

    @Test
    // 인증 증명 생략·위조 및 허용하지 않은 카테고리를 거절합니다.
    void rejectsMissingForgedProofAndUnknownCategory() throws Exception {
        postJson("/api/v1/auth/signup", "{\"email\":\"buyer@example.com\",\"password\":\"Password!123\",\"name\":\"김서윤\"}")
                .andExpect(status().isBadRequest());
        signup("buyer@example.com", "forged", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_INVALID_PROOF"));
        signup("buyer@example.com", "forged", ",\"interestCategories\":[\"UNKNOWN\"]")
                .andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero();
    }

    @Test
    // 증명은 인증한 이메일에만 사용 가능하고 실패 시 원래 증명을 유지합니다.
    void proofIsBoundToEmail() throws Exception {
        String proof = verify("buyer@example.com");
        signup("other@example.com", proof, "").andExpect(status().isBadRequest());
        signup("buyer@example.com", proof, "").andExpect(status().isCreated());
    }

    @Test
    // 동일 증명을 동시에 사용해도 한 건만 가입되고 설정은 중복 생성되지 않습니다.
    void consumesProofOnceUnderConcurrentSignup() throws Exception {
        String proof = verify("buyer@example.com");
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> request = () -> signup("buyer@example.com", proof, "")
                    .andReturn().getResponse().getStatus();
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 400);
        }
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(count("account_consents")).isEqualTo(1);
    }

    @Test
    // 오입력 횟수를 영속화하고 한도 초과 후에는 올바른 번호도 거절합니다.
    void persistsFailedAttempts() throws Exception {
        String challenge = issue("buyer@example.com");
        String wrong = codes.get("buyer@example.com").equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) confirm("buyer@example.com", challenge, wrong).andExpect(status().isBadRequest());
        assertThat(verifications.findById("buyer@example.com").orElseThrow().getAttempts()).isEqualTo(3);
        confirm("buyer@example.com", challenge, codes.get("buyer@example.com")).andExpect(status().isTooManyRequests());
    }

    @Test
    // 인증번호와 가입 증명은 서버 시각이 만료 시각에 도달하면 무효화됩니다.
    void rejectsExpiredCodeAndProof() throws Exception {
        String challenge = issue("buyer@example.com");
        now.updateAndGet(time -> time.plusSeconds(300));
        confirm("buyer@example.com", challenge, codes.get("buyer@example.com")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        String proof = verify("other@example.com");
        now.updateAndGet(time -> time.plusSeconds(900));
        signup("other@example.com", proof, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_INVALID_PROOF"));
    }

    @Test
    // 재전송 간격과 시간당 요청 제한을 검사하고 이전 요청 ID는 재발급 후 무효화합니다.
    void limitsRequestsAndInvalidatesOldChallenge() throws Exception {
        String old = issue("buyer@example.com");
        postJson("/api/v1/auth/email-verifications", "{\"email\":\"buyer@example.com\"}").andExpect(status().isTooManyRequests());
        now.updateAndGet(time -> time.plusSeconds(60));
        issue("buyer@example.com");
        confirm("buyer@example.com", old, codes.get("buyer@example.com")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_INVALID_CHALLENGE"));
        now.updateAndGet(time -> time.plusSeconds(60));
        issue("buyer@example.com");
        now.updateAndGet(time -> time.plusSeconds(60));
        postJson("/api/v1/auth/email-verifications", "{\"email\":\"buyer@example.com\"}").andExpect(status().isTooManyRequests());
        now.updateAndGet(time -> time.plusSeconds(3600));
        issue("buyer@example.com");
    }

    @Test
    // 인증 후 재발급하면 이전 가입 증명도 사용할 수 없습니다.
    void resendInvalidatesProof() throws Exception {
        String proof = verify("buyer@example.com");
        now.updateAndGet(time -> time.plusSeconds(60));
        issue("buyer@example.com");
        signup("buyer@example.com", proof, "").andExpect(status().isBadRequest());
    }

    @Test
    // 발송 실패 시 DB 변경을 롤백하여 재요청을 막지 않습니다.
    void rollsBackOnMailFailure() throws Exception {
        doThrow(new EmailVerificationException(EmailVerificationErrorCode.DELIVERY_FAILED))
                .when(mail).send(anyString(), anyString(), any(Instant.class));
        postJson("/api/v1/auth/email-verifications", "{\"email\":\"buyer@example.com\"}")
                .andExpect(status().isServiceUnavailable());
        assertThat(verifications.existsById("buyer@example.com")).isFalse();
    }

    @Test
    // 이름 검증에 실패하면 회원과 설정을 생성하거나 증명을 소비하지 않습니다.
    void requiresNameWithoutConsumingProof() throws Exception {
        String proof = verify("buyer@example.com");
        postJson("/api/v1/auth/signup", body("buyer@example.com", proof, "").replace("김서윤", "   "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("name"));
        assertThat(verifications.findById("buyer@example.com").orElseThrow().getConsumedAt()).isNull();
        assertThat(accounts.count()).isZero();
        assertThat(count("account_consents")).isZero();
    }

    @Test
    // 이미 가입한 이메일에는 인증번호를 새로 발급하지 않습니다.
    void rejectsRegisteredEmail() throws Exception {
        signup("buyer@example.com", verify("buyer@example.com"), "").andExpect(status().isCreated());
        postJson("/api/v1/auth/email-verifications", "{\"email\":\"buyer@example.com\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    // 공개 API는 고정 카테고리와 MVP 알림 두 종류를 제공합니다.
    void exposesSignupOptions() throws Exception {
        mvc.perform(get("/api/v1/signup-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(12))
                .andExpect(jsonPath("$.notificationTypes.length()").value(2));
    }

    // 인증번호는 API 응답이 아닌 대체 메일 발송기에서 읽습니다.
    private String issue(String email) throws Exception {
        String response = postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + email + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.challengeId");
    }

    // 실제 인증 흐름을 거쳐 가입 증명을 취득합니다.
    private String verify(String email) throws Exception {
        String challenge = issue(email);
        String response = confirm(email, challenge, codes.get(email)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.verificationToken");
    }

    private ResultActions confirm(String email, String challenge, String code) throws Exception {
        return postJson("/api/v1/auth/email-verifications/confirm",
                "{\"email\":\"" + email + "\",\"challengeId\":\"" + challenge + "\",\"code\":\"" + code + "\"}");
    }
    private ResultActions signup(String email, String proof, String options) throws Exception {
        return postJson("/api/v1/auth/signup", body(email, proof, options));
    }
    private String body(String email, String proof, String options) {
        return "{\"email\":\"" + email + "\",\"password\":\"Password!123\",\"name\":\"김서윤\",\"verificationToken\":\""
                + proof + "\"" + options + "}";
    }
    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    // 테스트 내부에서 지정한 고정 테이블만 조회합니다.
    private long count(String table) {
        return jdbc.queryForObject("select count(*) from account." + table, Long.class);
    }
}
