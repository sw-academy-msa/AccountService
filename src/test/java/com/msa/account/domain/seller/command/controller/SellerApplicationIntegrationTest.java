package com.msa.account.domain.seller.command.controller;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.admin.command.service.AdminBootstrapService;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.seller.entity.SellerApplication;
import com.msa.account.domain.seller.exception.*;
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

/** 실제 PostgreSQL과 API로 권한·소유권·동시 심사·롤백을 검증하고 객체 저장소만 대체합니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties={"spring.profiles.active=test","account.seller.max-certificate-bytes=1024"})
@AutoConfigureMockMvc
class SellerApplicationIntegrationTest {
    private static final String BASE="/api/v1/seller-applications";
    private static final String ADMIN="/api/v1/admin/seller-applications";
    private static final byte[] PDF="%PDF-1.7\nlocal-test\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final String JSON="""
        {"sellerDisplayName":"종이상점","contactName":"판매담당자","contactEmail":"seller@example.com",
         "contactPhone":"010-1234-5678","bankName":"신한은행","bankAccountNumber":"000-1234-5678",
         "dispatchAddress":{"postalCode":"06236","roadAddress":"서울 강남구 테헤란로 152","detailAddress":"12층"},
         "returnAddress":{"postalCode":"06236","roadAddress":"서울 강남구 테헤란로 152","detailAddress":"12층"}}
        """;
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired SellerApplicationRepository applications;
    @Autowired SellerCertificateRepository certificates;
    @Autowired SellerCertificateReviewRepository reviews;
    @Autowired SellerProfileRepository profiles;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AdminBootstrapService bootstrap;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean VerificationMailSender mail;
    @MockitoBean CertificateStorage storage;
    private Account buyer,other,admin,secondAdmin;

    @BeforeEach
    // 테스트 계정은 인증 완료 상태로 만들며 실제 메일·AWS API는 호출하지 않습니다.
    void setUp() {
        profiles.deleteAll(); reviews.deleteAll(); certificates.deleteAll(); applications.deleteAll();
        tokens.deleteAll(); sessions.deleteAll(); accounts.deleteAll();
        buyer=account("buyer@example.com",false); other=account("other@example.com",false);
        admin=account("admin@example.com",true); secondAdmin=account("admin2@example.com",true);
        when(storage.get(anyString())).thenReturn(PDF);
    }
    @AfterEach
    // 기존 회원 테스트와 컨텍스트가 달라도 참조 데이터를 명시적으로 정리합니다.
    void clean() { profiles.deleteAll(); reviews.deleteAll(); certificates.deleteAll(); applications.deleteAll(); }

    @Test
    void submitsAndShowsOnlyMaskedDetails() throws Exception {
        String id=submit();
        mvc.perform(get(BASE+"/me").with(as(buyer,false))).andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(id)).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptNumber").value(1)).andExpect(jsonPath("$.maskedBankAccount").value("***5678"))
                .andExpect(jsonPath("$.bankAccountNumber").doesNotExist()).andExpect(jsonPath("$.objectKey").doesNotExist());
        String encrypted=jdbc.queryForObject("select bank_account_encrypted from account.seller_applications where id=?",
                String.class,UUID.fromString(id));
        assertThat(encrypted).startsWith("v1:").doesNotContain("00012345678");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='account' "
                +"and table_name='seller_certificates' and column_name='content'",Long.class)).isZero();
        mvc.perform(get(BASE+"/"+id+"/certificate").with(as(buyer,false)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF)).andExpect(header().string("Cache-Control","no-store"));
    }

    @Test
    void requiresOwnAccountAndAdminPermissions() throws Exception {
        mvc.perform(multipart(BASE).file(json(JSON)).file(pdf())).andExpect(status().isUnauthorized());
        String id=submit();
        mvc.perform(get(BASE+"/"+id).with(as(other,false))).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/"+id+"/certificate").with(as(other,false))).andExpect(status().isNotFound());
        mvc.perform(get(ADMIN).with(as(buyer,false))).andExpect(status().isForbidden());
        // JWT에 ADMIN이 남아 있어도 실제 DB 권한이 없으면 조회와 심사를 차단합니다.
        mvc.perform(get(ADMIN).with(as(other,true))).andExpect(status().isForbidden());
        mvc.perform(post(ADMIN+"/"+id+"/approve").with(as(other,true))).andExpect(status().isForbidden());
    }

    @Test
    void blocksPendingEditsAndDuplicateApplications() throws Exception {
        String id=submit();
        upload(buyer,JSON,pdf()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELLER_ALREADY_APPLIED"));
        mvc.perform(put(BASE+"/"+id).with(as(buyer,false)).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isMethodNotAllowed());
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void approvesOnlyAfterCurrentReviewerReadsPdfAndKeepsBuyer() throws Exception {
        String id=submit();
        approve(admin,id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELLER_CERTIFICATE_REVIEW_REQUIRED"));
        read(admin,id).andExpect(status().isOk());
        mvc.perform(get(ADMIN+"/"+id).with(as(admin,true))).andExpect(jsonPath("$.certificateReviewed").value(true));
        approve(secondAdmin,id).andExpect(status().isConflict());
        approve(admin,id).andExpect(status().isNoContent());
        approve(admin,id).andExpect(status().isNoContent());
        assertThat(roleCount(buyer,Role.BUYER)).isEqualTo(1);
        assertThat(roleCount(buyer,Role.SELLER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select granted_by from account.account_roles where account_id=? and role='SELLER'",
                UUID.class,buyer.getId())).isEqualTo(admin.getId());
        upload(buyer,JSON,pdf()).andExpect(status().isConflict());
    }

    @Test
    void preservesRejectedHistoryAndAllowsNewAttempt() throws Exception {
        String old=submit();
        reject(admin,old,"서류를 다시 제출해 주세요.").andExpect(status().isNoContent());
        reject(admin,old,"서류를 다시 제출해 주세요.").andExpect(status().isNoContent());
        mvc.perform(get(BASE+"/me").with(as(buyer,false))).andExpect(jsonPath("$.rejectionReason").value("서류를 다시 제출해 주세요."));
        String next=submit();
        assertThat(next).isNotEqualTo(old);
        mvc.perform(get(BASE+"/me").with(as(buyer,false))).andExpect(jsonPath("$.attemptNumber").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get(BASE+"/me/history").with(as(buyer,false))).andExpect(jsonPath("$.length()").value(2));
        read(admin,old).andExpect(status().isOk());
        approve(admin,old).andExpect(status().isConflict());
        assertThat(roleCount(buyer,Role.SELLER)).isZero();
    }

    @Test
    void validatesFormAndPdf() throws Exception {
        upload(buyer,JSON.replace("판매담당자"," "),pdf()).andExpect(status().isBadRequest());
        upload(buyer,JSON.replace("000-1234-5678","1----2"),pdf()).andExpect(status().isBadRequest());
        upload(buyer,JSON,new MockMultipartFile("certificate","wrong.txt","text/plain",PDF)).andExpect(status().isBadRequest());
        upload(buyer,JSON,new MockMultipartFile("certificate","fake.pdf","application/pdf","not-pdf".getBytes()))
                .andExpect(status().isBadRequest());
        upload(buyer,JSON,new MockMultipartFile("certificate","big.pdf","application/pdf",new byte[1025]))
                .andExpect(status().isContentTooLarge());
        mvc.perform(multipart(BASE).file(json(JSON)).file(pdf()).file(pdf()).with(as(buyer,false)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(BASE).file(json(JSON)).with(as(buyer,false))).andExpect(status().isBadRequest());
        assertThat(applications.count()).isZero();
        verify(storage,never()).put(anyString(),any());
    }

    @Test
    void validatesRejectionAndPagination() throws Exception {
        String id=submit();
        reject(admin,id," ").andExpect(status().isBadRequest());
        mvc.perform(get(ADMIN).with(as(admin,true)).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get(ADMIN).with(as(admin,true)).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get(ADMIN).with(as(admin,true)).param("status","PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsSuspendedAndUnverifiedApplicantsAndRevokedAdmin() throws Exception {
        jdbc.update("update account.accounts set status='SUSPENDED' where id=?",buyer.getId());
        upload(buyer,JSON,pdf()).andExpect(status().isForbidden());
        jdbc.update("update account.accounts set status='ACTIVE',email_verified_at=null where id=?",buyer.getId());
        upload(buyer,JSON,pdf()).andExpect(status().isForbidden());
        jdbc.update("delete from account.account_roles where account_id=? and role='ADMIN'",admin.getId());
        mvc.perform(get(ADMIN).with(as(admin,true))).andExpect(status().isForbidden());
    }

    @Test
    void storageFailureRollsBackApplicationAndAttemptsCleanup() throws Exception {
        doThrow(new SellerException(SellerErrorCode.CERTIFICATE_UNAVAILABLE)).when(storage).put(anyString(),any());
        upload(buyer,JSON,pdf()).andExpect(status().isServiceUnavailable());
        assertThat(applications.count()).isZero();
        assertThat(certificates.count()).isZero();
        verify(storage).delete(anyString());
    }

    @Test
    void failedPdfReadDoesNotAuthorizeApproval() throws Exception {
        String id=submit();
        when(storage.get(anyString())).thenThrow(new SellerException(SellerErrorCode.CERTIFICATE_UNAVAILABLE));
        read(admin,id).andExpect(status().isServiceUnavailable());
        assertThat(reviews.count()).isZero();
        approve(admin,id).andExpect(status().isConflict());
    }

    @Test
    void approvalAndRoleGrantRollbackTogether() throws Exception {
        String id=submit(); read(admin,id).andExpect(status().isOk());
        // 심사 상태 저장이 실패하는 실제 DB 제약으로 권한 부여까지 되돌아가는지 확인합니다.
        jdbc.execute("alter table account.seller_applications add constraint test_no_approval check (status <> 'APPROVED')");
        try {
            approve(admin,id).andExpect(status().isConflict());
            assertThat(roleCount(buyer,Role.SELLER)).isZero();
            mvc.perform(get(BASE+"/me").with(as(buyer,false))).andExpect(jsonPath("$.status").value("PENDING"));
        } finally { jdbc.execute("alter table account.seller_applications drop constraint test_no_approval"); }
    }

    @Test
    void concurrentSubmissionsCreateOnePendingApplication() throws Exception {
        var statuses=together(()->upload(buyer,JSON,pdf()).andReturn().getResponse().getStatus(),
                ()->upload(buyer,JSON,pdf()).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201,409);
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void concurrentApprovalAndRejectionHaveSingleOutcome() throws Exception {
        String id=submit(); read(admin,id).andExpect(status().isOk());
        var statuses=together(()->approve(admin,id).andReturn().getResponse().getStatus(),
                ()->reject(secondAdmin,id,"서류 확인 필요").andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(204,409);
        SellerApplication app=applications.findById(UUID.fromString(id)).orElseThrow();
        assertThat(roleCount(buyer,Role.SELLER)).isEqualTo(app.getStatus().name().equals("APPROVED")?1:0);
    }

    @Test
    void bootstrapGrantsAdminOnlyToExistingVerifiedAccountAndIsIdempotent() {
        bootstrap.grant("OTHER@example.com"); bootstrap.grant("other@example.com");
        assertThat(roleCount(other,Role.ADMIN)).isEqualTo(1);
        assertThat(roleCount(other,Role.BUYER)).isEqualTo(1);
        assertThatThrownBy(()->bootstrap.grant("missing@example.com")).isInstanceOf(IllegalStateException.class);
    }

    private Account account(String email,boolean adminRole) {
        var a=Account.registerBuyer(email,"not-used","테스트");
        a.initializeSignup(Instant.now(),Set.of(),false,false,false,Instant.now());
        if(adminRole) a.grantRole(Role.ADMIN,null);
        return accounts.saveAndFlush(a);
    }
    private MockMultipartFile json(String body) {
        return new MockMultipartFile("application","","application/json",body.getBytes(StandardCharsets.UTF_8));
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("certificate","business.pdf","application/pdf",PDF); }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(Account a,boolean adminRole) {
        return jwt().jwt(j->j.subject(a.getId().toString())).authorities(
                new SimpleGrantedAuthority(adminRole?"ROLE_ADMIN":"ROLE_BUYER"));
    }
    private ResultActions upload(Account a,String body,MockMultipartFile pdf) throws Exception {
        return mvc.perform(multipart(BASE).file(json(body)).file(pdf).with(as(a,false)));
    }
    private String submit() throws Exception {
        var response=upload(buyer,JSON,pdf()).andExpect(status().isCreated()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(),"$.applicationId");
    }
    private ResultActions read(Account a,String id) throws Exception {
        return mvc.perform(post(ADMIN+"/"+id+"/certificate-review").with(as(a,true)));
    }
    private ResultActions approve(Account a,String id) throws Exception {
        return mvc.perform(post(ADMIN+"/"+id+"/approve").with(as(a,true)));
    }
    private ResultActions reject(Account a,String id,String reason) throws Exception {
        return mvc.perform(post(ADMIN+"/"+id+"/reject").with(as(a,true))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\""+reason+"\"}"));
    }
    private long roleCount(Account a,Role role) {
        return jdbc.queryForObject("select count(*) from account.account_roles where account_id=? and role=?",
                Long.class,a.getId(),role.name());
    }
    // 두 API를 같은 시각에 시작시켜 PostgreSQL 잠금에 의한 결과를 확인합니다.
    private List<Integer> together(Callable<Integer> first,Callable<Integer> second) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=executor.submit(()->{gate.await(); return first.call();});
            var b=executor.submit(()->{gate.await(); return second.call();});
            gate.countDown();
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
}
