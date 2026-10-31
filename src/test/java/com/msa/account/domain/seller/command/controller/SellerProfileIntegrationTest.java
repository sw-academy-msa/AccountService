package com.msa.account.domain.seller.command.controller;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.storage.CertificateStorage;
import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 PostgreSQL에서 마이페이지 변경 범위·권한·승인 이력 보존·동시 최초 수정을 검증합니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.profiles.active=test", "account.seller.max-certificate-bytes=1024"})
@AutoConfigureMockMvc
class SellerProfileIntegrationTest {
    private static final String BASE = "/api/v1/sellers/me";
    private static final String ADDRESS = """
        {"postalCode":"06236","roadAddress":"서울 강남구 테헤란로 152","detailAddress":"12층"}
        """.trim();
    private static final String APPLICATION = """
        {"sellerDisplayName":"원래상점","contactName":"원래담당자","contactEmail":"original@example.com",
         "contactPhone":"010-1234-5678","bankName":"신한은행","bankAccountNumber":"000-1234-5678",
         "dispatchAddress":%s,"returnAddress":%s}
        """.formatted(ADDRESS, ADDRESS);
    private static final String CONTACT = """
        {"sellerDisplayName":"새상점","contactName":"새담당자","contactEmail":"NEW@example.com","contactPhone":"010-9999-8888"}
        """;
    private static final String BANK = """
        {"bankName":"새은행","bankAccountNumber":"111-2222-9999","accountHolderName":"예금주"}
        """;
    private static final String ADDRESSES = """
        {"dispatchAddress":{"postalCode":"04066","roadAddress":"서울 마포구 와우산로 94","detailAddress":"3층"},
         "returnAddress":{"postalCode":"13494","roadAddress":"경기 성남시 분당구 판교역로 235","detailAddress":"1층"}}
        """;
    private static final byte[] PDF = "%PDF-1.7\nlocal-test\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired SellerApplicationRepository applications;
    @Autowired SellerProfileRepository profiles;
    @Autowired SellerCertificateRepository certificates;
    @Autowired SellerCertificateReviewRepository reviews;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean VerificationMailSender mail;
    @MockitoBean CertificateStorage storage;
    private Account seller, other, buyer, admin;
    private String approvedId;

    @BeforeEach
    // 승인 API 자체로 프로필을 생성하여 실제 초기화 경로와 계정별 격리를 확인합니다.
    void setUp() throws Exception {
        clean(); tokens.deleteAll(); sessions.deleteAll(); accounts.deleteAll();
        seller = account("seller@example.com", false);
        other = account("other@example.com", false);
        buyer = account("buyer@example.com", false);
        admin = account("admin@example.com", true);
        when(storage.get(anyString())).thenReturn(PDF);
        approvedId = approve(seller);
        approve(other);
    }

    @AfterEach
    void clean() {
        profiles.deleteAll(); reviews.deleteAll(); certificates.deleteAll(); applications.deleteAll();
    }

    @Test
    // 승인 직후 현재 정보가 생성되고 예금주를 추측하지 않으며 승인 PDF를 조회할 수 있습니다.
    void readsApprovedProfileAndCertificate() throws Exception {
        view(seller).andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedApplicationId").value(approvedId))
                .andExpect(jsonPath("$.bankAccount.maskedBankAccount").value("***5678"))
                .andExpect(jsonPath("$.bankAccount.accountHolderName").isEmpty())
                .andExpect(jsonPath("$.bankAccountEncrypted").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(profiles.count()).isEqualTo(2);
        // 프로필의 approvedApplicationId로 기존 신청별 PDF API를 호출합니다.
        mvc.perform(get("/api/v1/seller-applications/" + approvedId + "/certificate").with(as(seller, "SELLER")))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
    }

    @Test
    // 담당자 변경은 판매자 현재 정보만 바꾸며 회원 정보·신청서·다른 판매자는 유지합니다.
    void updatesContactWithoutChangingAccountOrApplication() throws Exception {
        update(seller, "/contact", CONTACT).andExpect(status().isNoContent());
        view(seller).andExpect(jsonPath("$.sellerDisplayName").value("새상점"))
                .andExpect(jsonPath("$.contactEmail").value("new@example.com"))
                .andExpect(jsonPath("$.contactPhone").value("01099998888"));
        assertThat(accounts.findById(seller.getId()).orElseThrow().getName()).isEqualTo("회원이름");
        assertThat(accounts.findById(seller.getId()).orElseThrow().getEmail()).isEqualTo("seller@example.com");
        assertThat(applications.findById(UUID.fromString(approvedId)).orElseThrow().getContactName()).isEqualTo("원래담당자");
        view(other).andExpect(jsonPath("$.sellerDisplayName").value("원래상점"));
    }

    @Test
    // 계좌는 기존 암호화 규칙을 재사용하고 원본 신청의 계좌·심사 상태는 변경하지 않습니다.
    void updatesAndEncryptsBankAccount() throws Exception {
        update(seller, "/bank-account", BANK).andExpect(status().isNoContent());
        view(seller).andExpect(jsonPath("$.bankAccount.bankName").value("새은행"))
                .andExpect(jsonPath("$.bankAccount.maskedBankAccount").value("***9999"))
                .andExpect(jsonPath("$.bankAccount.accountHolderName").value("예금주"));
        String cipher = jdbc.queryForObject("select bank_account_encrypted from account.seller_profiles where account_id=?",
                String.class, seller.getId());
        assertThat(cipher).startsWith("v1:").doesNotContain("11122229999");
        var original = applications.findById(UUID.fromString(approvedId)).orElseThrow();
        assertThat(original.getMaskedBankAccount()).isEqualTo("***5678");
        assertThat(original.getStatus().name()).isEqualTo("APPROVED");
        view(other).andExpect(jsonPath("$.bankAccount.maskedBankAccount").value("***5678"));
    }

    @Test
    // 출고·반품 주소는 현재 프로필만 교체하고 승인 당시 주소는 그대로 남깁니다.
    void updatesAddressesWithoutRewritingApproval() throws Exception {
        update(seller, "/addresses", ADDRESSES).andExpect(status().isNoContent());
        view(seller).andExpect(jsonPath("$.dispatchAddress.postalCode").value("04066"))
                .andExpect(jsonPath("$.returnAddress.postalCode").value("13494"));
        assertThat(applications.findById(UUID.fromString(approvedId)).orElseThrow().getDispatchAddress().getPostalCode())
                .isEqualTo("06236");
        view(other).andExpect(jsonPath("$.dispatchAddress.postalCode").value("06236"));
    }

    @Test
    // JWT·DB 권한·승인 이력을 함께 확인하므로 권한만 위조하거나 미승인 계정으로 접근할 수 없습니다.
    void requiresCurrentSellerRoleAndActualApproval() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(as(seller, "BUYER"))).andExpect(status().isForbidden());
        view(buyer).andExpect(status().isForbidden());
        buyer.grantRole(Role.SELLER, null); accounts.saveAndFlush(buyer);
        view(buyer).andExpect(status().isForbidden());
        update(buyer, "/contact", CONTACT).andExpect(status().isForbidden());
    }

    @Test
    // 이미 발급한 SELLER JWT도 계정 정지나 현재 권한 회수를 우회할 수 없습니다.
    void rejectsSuspendedOrRevokedSeller() throws Exception {
        jdbc.update("update account.accounts set status='SUSPENDED' where id=?", seller.getId());
        view(seller).andExpect(status().isForbidden());
        update(seller, "/bank-account", BANK).andExpect(status().isForbidden());
        jdbc.update("update account.accounts set status='ACTIVE' where id=?", seller.getId());
        jdbc.update("delete from account.account_roles where account_id=? and role='SELLER'", seller.getId());
        view(seller).andExpect(status().isForbidden());
    }

    @Test
    // 생략·잘못된 형식은 기존 저장 정보를 변경하지 않습니다.
    void validatesAllEditForms() throws Exception {
        update(seller, "/contact", CONTACT.replace("새담당자", " ")).andExpect(status().isBadRequest());
        update(seller, "/contact", CONTACT.replace("010-9999-8888", "bad")).andExpect(status().isBadRequest());
        update(seller, "/bank-account", BANK.replace("111-2222-9999", "***9999")).andExpect(status().isBadRequest());
        update(seller, "/bank-account", BANK.replace("예금주", " ")).andExpect(status().isBadRequest());
        update(seller, "/addresses", "{}").andExpect(status().isBadRequest());
        update(seller, "/addresses", ADDRESSES.replace("04066", "1234")).andExpect(status().isBadRequest());
        view(seller).andExpect(jsonPath("$.sellerDisplayName").value("원래상점"))
                .andExpect(jsonPath("$.bankAccount.maskedBankAccount").value("***5678"));
    }

    @Test
    // 요청에 타인 ID를 끼워 넣어도 JWT의 본인 계정만 변경합니다.
    void ignoresClientSuppliedOwner() throws Exception {
        String input = CONTACT.trim().replace("}", ",\"accountId\":\"" + other.getId() + "\"}");
        update(seller, "/contact", input).andExpect(status().isNoContent());
        view(seller).andExpect(jsonPath("$.sellerDisplayName").value("새상점"));
        view(other).andExpect(jsonPath("$.sellerDisplayName").value("원래상점"));
    }

    @Test
    // 기존 승인 계정은 조회가 DB 행을 생성하지 않고 최초 수정에서만 현재 프로필을 만듭니다.
    void supportsLegacyApprovedAccountsWithoutWritingOnRead() throws Exception {
        profiles.deleteById(seller.getId());
        view(seller).andExpect(status().isOk()).andExpect(jsonPath("$.sellerDisplayName").value("원래상점"));
        assertThat(profiles.existsById(seller.getId())).isFalse();
        update(seller, "/contact", CONTACT).andExpect(status().isNoContent());
        assertThat(profiles.existsById(seller.getId())).isTrue();
        view(seller).andExpect(jsonPath("$.sellerDisplayName").value("새상점"));
    }

    @Test
    // 기존 승인 계정의 동시 최초 수정도 행 하나를 만들고 각 화면 변경 내용을 유지합니다.
    void concurrentFirstEditsPreserveBothChanges() throws Exception {
        profiles.deleteById(seller.getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var contact = executor.submit(() -> {
                gate.await(); return update(seller, "/contact", CONTACT).andReturn().getResponse().getStatus();
            });
            var bank = executor.submit(() -> {
                gate.await(); return update(seller, "/bank-account", BANK).andReturn().getResponse().getStatus();
            });
            gate.countDown();
            assertThat(List.of(contact.get(20, TimeUnit.SECONDS), bank.get(20, TimeUnit.SECONDS))).containsExactly(204, 204);
        }
        assertThat(profiles.count()).isEqualTo(2);
        view(seller).andExpect(jsonPath("$.sellerDisplayName").value("새상점"))
                .andExpect(jsonPath("$.bankAccount.maskedBankAccount").value("***9999"));
    }

    @Test
    // 중복 승인 요청은 변경된 프로필을 다시 신청 값으로 덮어쓰지 않습니다.
    void repeatedApprovalKeepsEditedProfile() throws Exception {
        update(seller, "/contact", CONTACT).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/seller-applications/" + approvedId + "/approve").with(as(admin, "ADMIN")))
                .andExpect(status().isNoContent());
        view(seller).andExpect(jsonPath("$.sellerDisplayName").value("새상점"));
        assertThat(profiles.count()).isEqualTo(2);
    }

    @Test
    // 최초 생성 중 변경 저장이 실패하면 프로필 행도 롤백되어 승인 원본으로 계속 조회됩니다.
    void failedFirstEditRollsBackProfileCreation() throws Exception {
        profiles.deleteById(seller.getId());
        jdbc.execute("alter table account.seller_profiles add constraint test_bank_update check (bank_name <> '새은행')");
        try {
            update(seller, "/bank-account", BANK).andExpect(status().isConflict());
            assertThat(profiles.existsById(seller.getId())).isFalse();
            view(seller).andExpect(jsonPath("$.bankAccount.maskedBankAccount").value("***5678"));
        } finally { jdbc.execute("alter table account.seller_profiles drop constraint test_bank_update"); }
    }

    private Account account(String email, boolean isAdmin) {
        var a = Account.registerBuyer(email, "not-used", "회원이름");
        a.initializeSignup(Instant.now(), Set.of(), false, false, false, Instant.now());
        if (isAdmin) a.grantRole(Role.ADMIN, null);
        return accounts.saveAndFlush(a);
    }
    // 승인 준비도 공개 API로 수행하여 프로필 생성과 승인 원자성을 우회하지 않습니다.
    private String approve(Account owner) throws Exception {
        var application = new MockMultipartFile("application", "", "application/json", APPLICATION.getBytes(StandardCharsets.UTF_8));
        var pdf = new MockMultipartFile("certificate", "business.pdf", "application/pdf", PDF);
        var response = mvc.perform(multipart("/api/v1/seller-applications").file(application).file(pdf).with(as(owner, "BUYER")))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.applicationId");
        mvc.perform(post("/api/v1/admin/seller-applications/" + id + "/certificate-review").with(as(admin, "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/seller-applications/" + id + "/approve").with(as(admin, "ADMIN")))
                .andExpect(status().isNoContent());
        return id;
    }
    private RequestPostProcessor as(Account a, String role) {
        return jwt().jwt(j -> j.subject(a.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
    private ResultActions view(Account a) throws Exception { return mvc.perform(get(BASE).with(as(a, "SELLER"))); }
    private ResultActions update(Account a, String suffix, String body) throws Exception {
        return mvc.perform(put(BASE + suffix).with(as(a, "SELLER")).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
