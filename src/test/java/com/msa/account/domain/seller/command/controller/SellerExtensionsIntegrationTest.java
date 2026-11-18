package com.msa.account.domain.seller.command.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.domain.account.command.service.WithdrawalEligibilityService;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.query.dto.response.WithdrawalEligibilityResponse;
import com.msa.account.domain.account.repository.*;
import com.msa.account.domain.address.repository.AddressRepository;
import com.msa.account.domain.auth.entity.AuthSession;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.seller.command.service.SellerApplicationRevisionService;
import com.msa.account.domain.seller.enums.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.storage.*;
import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.global.config.database.TestcontainersConfiguration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

/** 격리 PostgreSQL과 HTTP 경계에서 새 설정·서류 이력·기한·권한과 탈퇴 차단을 검증합니다. 실제 AWS나 거래 서비스는 호출하지 않습니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "account.seller.corrections.expiration-enabled=false",
            // 판매자 기능만 검증하므로 로컬 .env의 탈퇴 기능 설정과 분리합니다.
            "account.withdrawal.enabled=false"
        })
@AutoConfigureMockMvc
class SellerExtensionsIntegrationTest {
    private static final String ROOT = "/api/v1/sellers/me";
    private static final String ADDRESS =
            "{\"postalCode\":\"06236\",\"roadAddress\":\"테스트 도로명 주소\",\"detailAddress\":\"테스트"
                    + " 상세\"}";
    private static final String FORM =
            "{\"sellerDisplayName\":\"테스트 상점\",\"contactName\":\"테스트"
                + " 담당자\",\"contactEmail\":\"synthetic@example.com\",\"contactPhone\":\"010-0000-0000\",\"bankName\":\"테스트은행\",\"bankAccountNumber\":\"000123456789\",\"dispatchAddress\":"
                    + ADDRESS
                    + ",\"returnAddress\":"
                    + ADDRESS
                    + "}";
    private static final byte[] PDF =
            "%PDF-1.7\nfixture\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-11-06T00:00:00Z"));
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired SellerProfileRepository profiles;
    @Autowired SellerApplicationRepository applications;
    @Autowired SellerCertificateRepository certificates;
    @Autowired SellerCertificateReviewRepository reviews;
    @Autowired SellerAddressEntryRepository addressBook;
    @Autowired SellerCorrectionRequestRepository corrections;
    @Autowired AccountWithdrawalRepository withdrawals;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AddressRepository addresses;
    @Autowired SellerApplicationRevisionService revision;
    @MockitoBean VerificationMailSender mail;
    @MockitoBean CertificateStorage storage;
    @MockitoBean LogoStorage logos;
    @MockitoBean WithdrawalEligibilityService eligibility;
    @MockitoBean Clock clock;
    private Account seller, other, buyer, admin;
    private UUID approved;

    @BeforeEach
    void setup() throws Exception {
        now.set(Instant.parse("2026-11-06T00:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        tokens.deleteAll();
        sessions.deleteAll();
        corrections.deleteAll();
        addressBook.deleteAll();
        withdrawals.deleteAll();
        profiles.deleteAll();
        reviews.deleteAll();
        certificates.deleteAll();
        applications.deleteAll();
        addresses.deleteAll();
        accounts.deleteAll();
        when(storage.get(anyString())).thenReturn(PDF);
        when(eligibility.inspect(any())).thenReturn(WithdrawalEligibilityResponse.unavailable());
        when(eligibility.acquireClearance(any()))
                .thenReturn(WithdrawalEligibilityResponse.unavailable());
        seller = account("extension-seller@example.com", false);
        other = account("extension-other@example.com", false);
        buyer = account("extension-buyer@example.com", false);
        admin = account("extension-admin@example.com", true);
        approved = submit(seller);
        approve(approved);
        approve(submit(other));
    }

    @Test
    void storeUsesDtoAndChecksCurrentSeller() throws Exception {
        mvc.perform(
                        put(ROOT + "/store")
                                .with(as(seller))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"introduction\":\"  오래 쓰는 기본 상품  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introduction").value("오래 쓰는 기본 상품"));
        mvc.perform(get(ROOT + "/store").with(as(seller)))
                .andExpect(jsonPath("$.data.hasLogo").value(false))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(ROOT + "/store").with(as(buyer))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT + "/store")).andExpect(status().isUnauthorized());
        mvc.perform(
                        put(ROOT + "/store")
                                .with(as(seller))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"introduction\":\"" + "가".repeat(41) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoChecksBytesAndIsOnlyReadableByOwner() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] image = out.toByteArray();
        when(logos.get(anyString())).thenReturn(image);
        mvc.perform(
                        multipart(ROOT + "/store/logo")
                                .file(
                                        new MockMultipartFile(
                                                "image", "logo.png", "image/png", image))
                                .with(as(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasLogo").value(true))
                .andExpect(jsonPath("$.data.objectKey").doesNotExist());
        verify(logos)
                .put(
                        startsWith("seller-logos/" + seller.getId() + "/"),
                        eq("image/png"),
                        eq(image));
        mvc.perform(get(ROOT + "/store/logo").with(as(seller)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(image));
        mvc.perform(get(ROOT + "/store/logo").with(as(other))).andExpect(status().isNotFound());
        mvc.perform(delete(ROOT + "/store/logo").with(as(seller))).andExpect(status().isOk());
        mvc.perform(get(ROOT + "/store/logo").with(as(seller))).andExpect(status().isNotFound());
    }

    @Test
    void logoRejectsSpoofedAndOversizedFilesWithoutStorageWrite() throws Exception {
        mvc.perform(
                        multipart(ROOT + "/store/logo")
                                .file(new MockMultipartFile("image", "fake.png", "image/png", PDF))
                                .with(as(seller)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        multipart(ROOT + "/store/logo")
                                .file(
                                        new MockMultipartFile(
                                                "image",
                                                "big.png",
                                                "image/png",
                                                new byte[2 * 1024 * 1024 + 1]))
                                .with(as(seller)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(logos);
    }

    @Test
    void addressDefaultsRemainCompatibleWithProductProfile() throws Exception {
        String base = ROOT + "/address-book/DISPATCH";
        var first = mvc.perform(get(base).with(as(seller))).andExpect(status().isOk()).andReturn();
        String initial = JsonPath.read(first.getResponse().getContentAsString(), "$.data[0].id");
        assertThat(addressBook.count()).isZero();
        String next = createAddress("DISPATCH", "다른 출고지");
        mvc.perform(put(base + "/" + next + "/default").with(as(seller)))
                .andExpect(status().isOk());
        mvc.perform(get(ROOT).with(as(seller)))
                .andExpect(jsonPath("$.data.dispatchAddress.roadAddress").value("다른 출고지"));
        mvc.perform(get(base + "/" + next).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(delete(base + "/" + next).with(as(seller))).andExpect(status().isConflict());
        mvc.perform(delete(base + "/" + initial).with(as(seller))).andExpect(status().isOk());
        mvc.perform(get(ROOT + "/address-book/RETURN").with(as(seller)))
                .andExpect(jsonPath("$.data[0].address.roadAddress").value("테스트 도로명 주소"));
    }

    @Test
    void concurrentDefaultChangesLeaveOneDefault() throws Exception {
        String a = createAddress("RETURN", "첫 반품지"), b = createAddress("RETURN", "둘째 반품지");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks =
                    List.<Callable<Integer>>of(
                            () ->
                                    mvc.perform(
                                                    put(ROOT
                                                                    + "/address-book/RETURN/"
                                                                    + a
                                                                    + "/default")
                                                            .with(as(seller)))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus(),
                            () ->
                                    mvc.perform(
                                                    put(ROOT
                                                                    + "/address-book/RETURN/"
                                                                    + b
                                                                    + "/default")
                                                            .with(as(seller)))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus());
            for (var result : pool.invokeAll(tasks)) assertThat(result.get()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
        var result = mvc.perform(get(ROOT + "/address-book/RETURN").with(as(seller))).andReturn();
        List<Boolean> values =
                JsonPath.read(
                        result.getResponse().getContentAsString(), "$.data[*].defaultAddress");
        assertThat(values.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1);
    }

    @Test
    void legacyAddressUpdateSynchronizesNewDefaultEntry() throws Exception {
        createAddress("DISPATCH", "추가 출고지");
        mvc.perform(
                        put(ROOT + "/addresses")
                                .with(as(seller))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dispatchAddress\":"
                                                + ADDRESS.replace("테스트 도로명 주소", "기존 API 수정")
                                                + ",\"returnAddress\":"
                                                + ADDRESS
                                                + "}"))
                .andExpect(status().isOk());
        var response =
                mvc.perform(get(ROOT + "/address-book/DISPATCH").with(as(seller))).andReturn();
        List<String> values =
                JsonPath.read(
                        response.getResponse().getContentAsString(),
                        "$.data[?(@.defaultAddress == true)].address.roadAddress");
        assertThat(values).containsExactly("기존 API 수정");
    }

    @Test
    void correctionsCreateNewAttemptAndPreserveOriginalPdfAndReview() throws Exception {
        UUID application = submit(buyer);
        String key = certificates.findById(application).orElseThrow().getObjectKey();
        requestCorrection(application, "CERTIFICATE", "CONTACT");
        mvc.perform(
                        get("/api/v1/seller-applications/" + application + "/correction-request")
                                .with(as(other)))
                .andExpect(status().isNotFound());
        var response =
                mvc.perform(
                                multipart(
                                                "/api/v1/seller-applications/"
                                                        + application
                                                        + "/resubmissions")
                                        .file(form(contactPatch()))
                                        .file(pdf())
                                        .with(as(buyer)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID next =
                UUID.fromString(
                        JsonPath.read(
                                response.getResponse().getContentAsString(),
                                "$.data.applicationId"));
        assertThat(applications.findById(application).orElseThrow().getContactName())
                .isEqualTo("테스트 담당자");
        assertThat(certificates.findById(application).orElseThrow().getObjectKey()).isEqualTo(key);
        assertThat(certificates.findById(next).orElseThrow().getObjectKey()).isNotEqualTo(key);
        assertThat(applications.findById(next).orElseThrow().getAttemptNumber()).isEqualTo(2);
        mvc.perform(post("/api/v1/admin/seller-applications/" + next + "/approve").with(as(admin)))
                .andExpect(status().isConflict());
        approve(next);
    }

    @Test
    void correctionsRejectChangesOutsideRequestedFields() throws Exception {
        UUID application = submit(buyer);
        requestCorrection(application, "CONTACT");
        String unrelatedBank =
                contactPatch().substring(0, contactPatch().length() - 1)
                        + ",\"bankAccount\":{\"bankName\":\"테스트은행\",\"number\":\"999123456789\"}}";
        mvc.perform(
                        multipart("/api/v1/seller-applications/" + application + "/resubmissions")
                                .file(form(unrelatedBank))
                                .with(as(buyer)))
                .andExpect(status().isBadRequest());
        assertThat(applications.findAllByAccount_IdOrderByAttemptNumberDesc(buyer.getId()))
                .hasSize(1);
    }

    @Test
    void expiredDeadlineIsNotExtendedAndCommitsFailureState() throws Exception {
        UUID application = submit(buyer);
        requestCorrection(application, "CONTACT");
        now.set(now.get().plusSeconds(7200));
        mvc.perform(
                        get("/api/v1/seller-applications/" + application + "/correction-request")
                                .with(as(buyer)))
                .andExpect(jsonPath("$.data.state").value("EXPIRED"));
        mvc.perform(
                        multipart("/api/v1/seller-applications/" + application + "/resubmissions")
                                .file(form(contactPatch()))
                                .with(as(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_CORRECTION_EXPIRED"));
        assertThat(applications.findById(application).orElseThrow().getStatus())
                .isEqualTo(SellerApplicationStatus.EXPIRED);
        assertThat(
                        corrections
                                .findFirstByApplicationIdOrderByCreatedAtDesc(application)
                                .orElseThrow()
                                .getState()
                                .name())
                .isEqualTo("EXPIRED");
        assertThat(submit(buyer)).isNotEqualTo(application);
    }

    @Test
    void contactOnlyCorrectionDoesNotRequireHiddenBankOrAnotherPdf() throws Exception {
        UUID application = submit(buyer);
        requestCorrection(application, "CONTACT");
        String encrypted =
                applications.findById(application).orElseThrow().getBankAccountEncrypted();
        var response =
                mvc.perform(
                                multipart(
                                                "/api/v1/seller-applications/"
                                                        + application
                                                        + "/resubmissions")
                                        .file(form(contactPatch()))
                                        .with(as(buyer)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID next =
                UUID.fromString(
                        JsonPath.read(
                                response.getResponse().getContentAsString(),
                                "$.data.applicationId"));
        assertThat(applications.findById(next).orElseThrow().getBankAccountEncrypted())
                .isEqualTo(encrypted);
        assertThat(applications.findById(next).orElseThrow().getContactName()).isEqualTo("보완 담당자");
        assertThat(certificates.findById(next)).isPresent();
    }

    @Test
    void schedulerServiceExpiresAnOpenCorrection() throws Exception {
        UUID application = submit(buyer);
        requestCorrection(application, "CERTIFICATE");
        now.set(now.get().plusSeconds(7200));
        revision.expire(
                corrections
                        .findFirstByApplicationIdOrderByCreatedAtDesc(application)
                        .orElseThrow()
                        .getId());
        assertThat(applications.findById(application).orElseThrow().getStatus())
                .isEqualTo(SellerApplicationStatus.EXPIRED);
    }

    @Test
    void certificateReviewRestrictsSalesUntilFreshAdminApproval() throws Exception {
        mvc.perform(
                        put(ROOT + "/store")
                                .with(as(seller))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"introduction\":\"보존할 소개\"}"))
                .andExpect(status().isOk());
        String originalKey = certificates.findById(approved).orElseThrow().getObjectKey();
        UUID next = recheck();
        mvc.perform(get(ROOT).with(as(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_SALES_RESTRICTED"));
        mvc.perform(get(ROOT + "/review-status").with(as(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesRestricted").value(true));
        mvc.perform(multipart(ROOT + "/certificate-reviews").file(pdf()).with(as(seller)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/accounts/me").with(as(seller)))
                .andExpect(
                        jsonPath("$.data.roles")
                                .value(org.hamcrest.Matchers.hasItems("BUYER", "SELLER")));
        assertThat(certificates.findById(approved).orElseThrow().getObjectKey())
                .isEqualTo(originalKey);
        approve(next);
        mvc.perform(get(ROOT + "/store").with(as(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introduction").value("보존할 소개"));
        mvc.perform(get(ROOT).with(as(seller)))
                .andExpect(jsonPath("$.data.approvedApplicationId").value(next.toString()));
        assertThat(applications.findById(approved).orElseThrow().getStatus())
                .isEqualTo(SellerApplicationStatus.APPROVED);
    }

    @Test
    void rejectedCertificateReviewKeepsRestrictionAndAllowsAnotherReview() throws Exception {
        UUID next = recheck();
        mvc.perform(
                        post("/api/v1/admin/seller-applications/" + next + "/reject")
                                .with(as(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"서류 확인 필요\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(ROOT).with(as(seller))).andExpect(status().isForbidden());
        assertThat(recheck()).isNotEqualTo(next);
    }

    @Test
    void withdrawalNeverTreatsUnknownOrActiveTradeAsZero() throws Exception {
        mvc.perform(
                        delete("/api/v1/accounts/me")
                                .with(as(buyer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody("wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        delete("/api/v1/accounts/me")
                                .with(as(buyer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody("Password!123")))
                .andExpect(status().isServiceUnavailable());
        assertThat(accounts.findById(buyer.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
        when(eligibility.acquireClearance(buyer.getId()))
                .thenReturn(
                        WithdrawalEligibilityResponse.builder()
                                .verified(true)
                                .canWithdraw(false)
                                .activeOrders(1L)
                                .activeSales(0L)
                                .pendingSettlements(0L)
                                .build());
        mvc.perform(
                        delete("/api/v1/accounts/me")
                                .with(as(buyer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody("Password!123")))
                .andExpect(status().isConflict());
        assertThat(withdrawals.findById(buyer.getId())).isEmpty();
    }

    @Test
    void clearedWithdrawalRevokesRefreshAndIsIdempotent() throws Exception {
        when(eligibility.acquireClearance(buyer.getId()))
                .thenReturn(
                        WithdrawalEligibilityResponse.builder()
                                .verified(true)
                                .canWithdraw(true)
                                .activeOrders(0L)
                                .activeSales(0L)
                                .pendingSettlements(0L)
                                .build());
        var session =
                sessions.saveAndFlush(
                        AuthSession.builder()
                                .account(buyer)
                                .now(now.get())
                                .expiresAt(now.get().plusSeconds(3600))
                                .build());
        mvc.perform(
                        delete("/api/v1/accounts/me")
                                .with(as(buyer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody("Password!123")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
        var first = withdrawals.findById(buyer.getId()).orElseThrow().getWithdrawnAt();
        assertThat(accounts.findById(buyer.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(sessions.findById(session.getId()).orElseThrow().isActive(now.get())).isFalse();
        mvc.perform(get("/api/v1/accounts/me").with(as(buyer))).andExpect(status().isForbidden());
        now.set(now.get().plusSeconds(60));
        mvc.perform(
                        delete("/api/v1/accounts/me")
                                .with(as(buyer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody("Password!123")))
                .andExpect(status().isOk());
        assertThat(withdrawals.findById(buyer.getId()).orElseThrow().getWithdrawnAt())
                .isEqualTo(first);
    }

    private String contactPatch() {
        return "{\"contact\":{\"name\":\"보완"
                + " 담당자\",\"email\":\"synthetic@example.com\",\"phone\":\"01000000000\"}}";
    }

    private String withdrawBody(String password) {
        return "{\"currentPassword\":\"" + password + "\",\"confirmed\":true}";
    }

    private UUID recheck() throws Exception {
        var r =
                mvc.perform(multipart(ROOT + "/certificate-reviews").file(pdf()).with(as(seller)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return UUID.fromString(
                JsonPath.read(r.getResponse().getContentAsString(), "$.data.applicationId"));
    }

    private String createAddress(String type, String road) throws Exception {
        String body =
                "{\"label\":\"테스트 주소\",\"contactName\":\"테스트"
                        + " 담당자\",\"contactPhone\":\"01000000000\",\"address\":"
                        + ADDRESS.replace("테스트 도로명 주소", road)
                        + "}";
        var r =
                mvc.perform(
                                post(ROOT + "/address-book/" + type)
                                        .with(as(seller))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data.id");
    }

    private void requestCorrection(UUID id, String... fields) throws Exception {
        String items =
                String.join(
                        ",",
                        Arrays.stream(fields)
                                .map(f -> "{\"field\":\"" + f + "\",\"reason\":\"확인해 주세요\"}")
                                .toList());
        mvc.perform(
                        post("/api/v1/admin/seller-applications/" + id + "/correction-request")
                                .with(as(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"deadline\":\""
                                                + now.get().plusSeconds(3600)
                                                + "\",\"items\":["
                                                + items
                                                + "]}"))
                .andExpect(status().isOk());
    }

    private UUID submit(Account who) throws Exception {
        var r =
                mvc.perform(
                                multipart("/api/v1/seller-applications")
                                        .file(form(FORM))
                                        .file(pdf())
                                        .with(as(who)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return UUID.fromString(
                JsonPath.read(r.getResponse().getContentAsString(), "$.data.applicationId"));
    }

    private void approve(UUID id) throws Exception {
        mvc.perform(
                        post("/api/v1/admin/seller-applications/" + id + "/certificate-review")
                                .with(as(admin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/seller-applications/" + id + "/approve").with(as(admin)))
                .andExpect(status().isOk());
    }

    private MockMultipartFile form(String content) {
        return new MockMultipartFile(
                "application", "", "application/json", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("certificate", "business.pdf", "application/pdf", PDF);
    }

    private RequestPostProcessor as(Account account) {
        String[] roles =
                account.getId().equals(admin.getId())
                        ? new String[] {"ROLE_BUYER", "ROLE_ADMIN"}
                        : new String[] {"ROLE_BUYER", "ROLE_SELLER"};
        return jwt().jwt(j -> j.subject(account.getId().toString()))
                .authorities(
                        Arrays.stream(roles)
                                .map(SimpleGrantedAuthority::new)
                                .toArray(SimpleGrantedAuthority[]::new));
    }

    private Account account(String email, boolean administrator) {
        var a = Account.registerBuyer(email, encoder.encode("Password!123"), "테스트 회원");
        a.initializeSignup(now.get(), Set.of(), false, false, false, now.get());
        if (administrator) a.grantRole(Role.ADMIN, null, now.get());
        return accounts.saveAndFlush(a);
    }

    @Autowired com.msa.account.domain.outbox.repository.OutboxEventRepository notificationOutbox;

    @Autowired
    com.msa.account.domain.seller.command.service.SellerReviewCommandService notificationReview;

    /** 승인과 반려의 실제 서비스 경로가 Outbox를 남기며 재요청에는 같은 이벤트를 유지합니다. */
    @Test
    void reviewChangesStoreOneNotificationWithoutPrivateReason() throws Exception {
        var app = applications.findById(approved).orElseThrow();
        UUID approvedEvent =
                UUID.nameUUIDFromBytes(
                        ("ACCOUNT:SELLER_APPROVED:"
                                        + approved
                                        + ":"
                                        + app.notificationVersion()
                                        + ":"
                                        + seller.getId())
                                .getBytes(StandardCharsets.UTF_8));
        String first = notificationOutbox.findById(approvedEvent).orElseThrow().payload;
        notificationReview.approve(admin.getId(), approved);
        assertThat(notificationOutbox.findById(approvedEvent).orElseThrow().payload)
                .isEqualTo(first);
        var applicant = account("notice-" + UUID.randomUUID() + "@example.com", false);
        UUID application = submit(applicant);
        notificationReview.reject(admin.getId(), application, "합성 비공개 심사 사유");
        var rejected = applications.findById(application).orElseThrow();
        UUID rejectedEvent =
                UUID.nameUUIDFromBytes(
                        ("ACCOUNT:SELLER_REJECTED:"
                                        + application
                                        + ":"
                                        + rejected.notificationVersion()
                                        + ":"
                                        + applicant.getId())
                                .getBytes(StandardCharsets.UTF_8));
        String body = notificationOutbox.findById(rejectedEvent).orElseThrow().payload;
        assertThat(body).contains("SELLER_REJECTED").doesNotContain("합성 비공개 심사 사유");
        notificationReview.reject(admin.getId(), application, "합성 비공개 심사 사유");
        assertThat(notificationOutbox.findById(rejectedEvent).orElseThrow().payload)
                .isEqualTo(body);
    }
}
