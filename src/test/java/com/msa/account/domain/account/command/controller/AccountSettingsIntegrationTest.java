package com.msa.account.domain.account.command.controller;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.InterestCategory;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.entity.AuthSession;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 PostgreSQL에서 소유권·입력 검증·동의 이력·동시 갱신의 일관성을 확인합니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
class AccountSettingsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean VerificationMailSender mail;
    private Account buyer;
    private Account other;
    private static final String BASE = "/api/v1/accounts/me";

    @BeforeEach
    // 이메일 발송 없이 두 회원을 만들고 다른 회원 데이터와의 격리를 검사합니다.
    void setUp() {
        tokens.deleteAll(); sessions.deleteAll(); accounts.deleteAll();
        buyer = create("buyer@example.com");
        other = create("other@example.com");
    }
    private Account create(String email) {
        var account = Account.registerBuyer(email, encoder.encode("Password!123"), "기존이름");
        account.initializeSignup(Instant.now(), Set.of(InterestCategory.FASHION), true, true, true, Instant.now());
        return accounts.saveAndFlush(account);
    }

    @Test
    // 인증 없이 조회·수정할 수 없고 요청 본문의 회원 ID로 대상을 바꿀 수 없습니다.
    void requiresAuthenticationAndUsesPrincipal() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(patch(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경\"}"))
                .andExpect(status().isUnauthorized());
        update("PATCH", "", "{\"name\":\"  새이름  \",\"accountId\":\"" + other.getId() + "\"}")
                .andExpect(status().isNoContent());
        view().andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(accounts.findById(other.getId()).orElseThrow().getName()).isEqualTo("기존이름");
    }

    @Test
    // 겹치는 카테고리를 유지하면서 목록을 교체하고 빈 목록으로 모두 해제할 수 있습니다.
    void replacesAndClearsInterests() throws Exception {
        String selection = "{\"interestCategories\":[\"FASHION\",\"ELECTRONICS\"]}";
        update("PUT", "/interests", selection).andExpect(status().isNoContent());
        update("PUT", "/interests", selection).andExpect(status().isNoContent());
        view().andExpect(jsonPath("$.interestCategories.length()").value(2));
        update("PUT", "/interests", "{\"interestCategories\":[]}").andExpect(status().isNoContent());
        view().andExpect(jsonPath("$.interestCategories").isEmpty());
    }

    @Test
    // 잘못된 입력은 기존 설정을 지우지 않고 거절합니다.
    void rejectsInvalidInputs() throws Exception {
        update("PATCH", "", "{\"name\":\"   \"}").andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"interestCategories\":null}",
                "{\"interestCategories\":[null]}", "{\"interestCategories\":[\"UNKNOWN\"]}"))
            update("PUT", "/interests", body).andExpect(status().isBadRequest());
        update("PUT", "/notification-settings", "{\"saleStart\":true}").andExpect(status().isBadRequest());
        update("PUT", "/marketing-consent", "{}").andExpect(status().isBadRequest());
        view().andExpect(jsonPath("$.interestCategories.length()").value(1))
                .andExpect(jsonPath("$.marketingConsent.agreed").value(true));
    }

    @Test
    // 철회와 재동의는 실제 변경마다 기록하고 동일 요청은 중복 기록하지 않습니다.
    void tracksConsentChangesSeparatelyFromNotifications() throws Exception {
        update("PUT", "/marketing-consent", "{\"marketingConsent\":false}").andExpect(status().isNoContent());
        update("PUT", "/marketing-consent", "{\"marketingConsent\":false}").andExpect(status().isNoContent());
        view().andExpect(jsonPath("$.marketingConsent.agreed").value(false))
                .andExpect(jsonPath("$.marketingConsent.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.notificationSettings.saleStart").value(true));
        assertThat(historyCount()).isEqualTo(2);
        update("PUT", "/marketing-consent", "{\"marketingConsent\":true}").andExpect(status().isNoContent());
        update("PUT", "/notification-settings", "{\"saleStart\":false,\"personalizedRecommendation\":false}")
                .andExpect(status().isNoContent());
        view().andExpect(jsonPath("$.marketingConsent.agreed").value(true))
                .andExpect(jsonPath("$.notificationSettings.saleStart").value(false));
        assertThat(historyCount()).isEqualTo(3);
    }

    @Test
    // 동시 철회 요청도 계정 잠금으로 직렬화하여 이력을 한 번만 생성합니다.
    void concurrentWithdrawalCreatesOneHistory() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> task = () -> {
                gate.await();
                return update("PUT", "/marketing-consent", "{\"marketingConsent\":false}")
                        .andReturn().getResponse().getStatus();
            };
            var a = executor.submit(task); var b = executor.submit(task); gate.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactly(204, 204);
        }
        assertThat(historyCount()).isEqualTo(2);
    }

    @Test
    // 정지된 계정은 기존 JWT가 있어도 설정을 조회하거나 변경할 수 없습니다.
    void rejectsSuspendedAccount() throws Exception {
        jdbc.update("update account.accounts set status='SUSPENDED' where id=?", buyer.getId());
        view().andExpect(status().isForbidden());
        update("PUT", "/marketing-consent", "{\"marketingConsent\":false}").andExpect(status().isForbidden());
        assertThat(historyCount()).isEqualTo(1);
    }

    @Test
    // 현재 비밀번호가 틀리면 세션을 유지하고 성공하면 모든 기기의 재발급 권한을 폐기합니다.
    void changesPasswordAndRevokesSessions() throws Exception {
        sessions.saveAndFlush(new AuthSession(buyer, Instant.now(), Instant.now().plusSeconds(3600)));
        sessions.saveAndFlush(new AuthSession(buyer, Instant.now(), Instant.now().plusSeconds(3600)));
        update("PUT", "/password", "{\"currentPassword\":\"Wrong!123\",\"newPassword\":\"Changed!456\"}")
                .andExpect(status().isUnauthorized());
        assertThat(activeSessions()).isEqualTo(2);
        update("PUT", "/password", "{\"currentPassword\":\"Password!123\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest());
        update("PUT", "/password", "{\"currentPassword\":\"Password!123\",\"newPassword\":\"Changed!456\"}")
                .andExpect(status().isNoContent());
        assertThat(activeSessions()).isZero();
        assertThat(encoder.matches("Changed!456", accounts.findById(buyer.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(encoder.matches("Password!123", accounts.findById(other.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    // JWT 서명을 대체하되 실제 보안 필터·컨트롤러·트랜잭션·DB는 그대로 사용합니다.
    private ResultActions update(String method, String path, String body) throws Exception {
        var request = method.equals("PATCH") ? patch(BASE + path) : put(BASE + path);
        return mvc.perform(request.with(jwt().jwt(token -> token.subject(buyer.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private ResultActions view() throws Exception {
        return mvc.perform(get(BASE).with(jwt().jwt(token -> token.subject(buyer.getId().toString()))));
    }
    private long historyCount() {
        return jdbc.queryForObject("select count(*) from account.account_consent_histories where account_id=?", Long.class, buyer.getId());
    }
    private long activeSessions() {
        return jdbc.queryForObject("select count(*) from account.auth_sessions where account_id=? and revoked_at is null", Long.class, buyer.getId());
    }
}
