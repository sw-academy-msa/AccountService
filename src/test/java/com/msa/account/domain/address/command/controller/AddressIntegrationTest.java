package com.msa.account.domain.address.command.controller;

import com.jayway.jsonpath.JsonPath;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.address.repository.AddressRepository;
import com.msa.account.domain.auth.repository.*;
import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.global.config.database.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 DB에서 구매자 자격·소유권·기본 배송지 정책과 동시 요청을 검증합니다. 외부 API는 호출하지 않습니다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
class AddressIntegrationTest {
    private static final String BASE = "/api/v1/accounts/me/addresses";
    private static final String BODY = """
            {"label":"집","recipientName":"수령인","recipientPhone":"010-1234-5678",
             "postalCode":"06236","roadAddress":"서울 강남구 테헤란로 152",
             "detailAddress":"12층","deliveryMemo":"문 앞에 놓아주세요"}
            """;
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired AddressRepository addresses;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AuthSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean VerificationMailSender mail;
    private Account buyer;
    private Account other;

    @BeforeEach
    // JWT 인증만 대체하므로 테스트 계정에는 실제 비밀번호 해시나 이메일 발송이 필요하지 않습니다.
    void setUp() {
        addresses.deleteAll(); tokens.deleteAll(); sessions.deleteAll(); accounts.deleteAll();
        buyer = createAccount("buyer@example.com");
        other = createAccount("other@example.com");
    }
    @AfterEach
    // 같은 Spring 컨텍스트를 쓰는 기존 회원 테스트에 배송지 FK를 남기지 않습니다.
    void cleanAddresses() { addresses.deleteAll(); }

    @Test
    // 기본 주소를 먼저 반환하고 선택 주소 수정 시 기본 여부는 유지합니다.
    void registersReadsAndUpdatesAddresses() throws Exception {
        mvc.perform(get(BASE).with(jwt().jwt(j -> j.subject(buyer.getId().toString()))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        String first = create(buyer.getId());
        String second = create(buyer.getId());
        getOne(buyer.getId(), first).andExpect(jsonPath("$.defaultAddress").value(true))
                .andExpect(jsonPath("$.recipientPhone").value("01012345678"));
        getOne(buyer.getId(), second).andExpect(jsonPath("$.defaultAddress").value(false));
        perform(buyer.getId(), "PUT", "/" + second, BODY.replace("집", "회사")).andExpect(status().isNoContent());
        perform(buyer.getId(), "PUT", "/" + second + "/default", null).andExpect(status().isNoContent());
        mvc.perform(get(BASE).with(jwt().jwt(j -> j.subject(buyer.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].addressId").value(second))
                .andExpect(jsonPath("$[0].label").value("회사"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(defaultCount()).isEqualTo(1);
    }

    @Test
    // 타인의 식별자를 알아도 조회·수정·삭제·기본 지정 모두 같은 404로 거절합니다.
    void rejectsOtherOwners() throws Exception {
        String own = create(buyer.getId());
        String theirs = create(other.getId());
        getOne(buyer.getId(), theirs).andExpect(status().isNotFound());
        perform(buyer.getId(), "PUT", "/" + theirs, BODY).andExpect(status().isNotFound());
        perform(buyer.getId(), "DELETE", "/" + theirs, null).andExpect(status().isNotFound());
        perform(buyer.getId(), "PUT", "/" + theirs + "/default", null).andExpect(status().isNotFound());
        getOne(buyer.getId(), UUID.randomUUID().toString()).andExpect(status().isNotFound());
        getOne(buyer.getId(), own).andExpect(jsonPath("$.defaultAddress").value(true));
        assertThat(addresses.count()).isEqualTo(2);
    }

    @Test
    // 다른 주소가 남으면 기본 주소 삭제를 차단하고 유일한 주소는 삭제할 수 있습니다.
    void enforcesDefaultDeletionPolicyAndAllowsRestart() throws Exception {
        String first = create(buyer.getId()), second = create(buyer.getId());
        perform(buyer.getId(), "DELETE", "/" + first, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_DEFAULT_CHANGE_REQUIRED"));
        perform(buyer.getId(), "PUT", "/" + second + "/default", null).andExpect(status().isNoContent());
        perform(buyer.getId(), "DELETE", "/" + first, null).andExpect(status().isNoContent());
        perform(buyer.getId(), "DELETE", "/" + second, null).andExpect(status().isNoContent());
        assertThat(addresses.countByAccountId(buyer.getId())).isZero();
        getOne(buyer.getId(), create(buyer.getId())).andExpect(jsonPath("$.defaultAddress").value(true));
    }

    @Test
    // 쿠키만으로 호출할 수 없고 JWT의 오래된 BUYER 주장보다 현재 DB 권한을 우선합니다.
    void requiresAuthenticationAndCurrentBuyerRole() throws Exception {
        mvc.perform(post(BASE).cookie(new Cookie("stockdrop_refresh", "opaque"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        jdbc.update("delete from account.account_roles where account_id=?", buyer.getId());
        perform(buyer.getId(), "POST", "", BODY).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADDRESS_BUYER_REQUIRED"));
        mvc.perform(get(BASE).with(jwt().jwt(j -> j.subject(buyer.getId().toString()))))
                .andExpect(status().isForbidden());
        assertThat(addresses.count()).isZero();
    }

    @Test
    // 판매자도 구매자 권한을 유지하므로 배송지를 등록할 수 있습니다.
    void allowsBuyerWithSellerRole() throws Exception {
        buyer.grantRole(Role.SELLER, other.getId()); accounts.saveAndFlush(buyer);
        getOne(buyer.getId(), create(buyer.getId())).andExpect(status().isOk());
    }

    @Test
    // 기존 JWT가 있어도 정지·미인증 계정의 주소 사용은 거절합니다.
    void rejectsUnavailableAccount() throws Exception {
        jdbc.update("update account.accounts set status='SUSPENDED' where id=?", buyer.getId());
        perform(buyer.getId(), "POST", "", BODY).andExpect(status().isForbidden());
        jdbc.update("update account.accounts set status='ACTIVE', email_verified_at=null where id=?", buyer.getId());
        perform(buyer.getId(), "POST", "", BODY).andExpect(status().isForbidden());
    }

    @Test
    // 카카오 검색 결과를 받는 API라도 서버에서 필수값과 길이·형식은 검증합니다.
    void validatesInputBeforeSaving() throws Exception {
        for (String body : List.of("{}", BODY.replace("06236", "1234"),
                BODY.replace("010-1234-5678", "invalid"), BODY.replace("수령인", "  "),
                BODY.replace("서울 강남구 테헤란로 152", " "), BODY.replace("12층", "가".repeat(201)))) {
            perform(buyer.getId(), "POST", "", body).andExpect(status().isBadRequest());
        }
        assertThat(addresses.count()).isZero();
    }

    @Test
    // 소유자와 기본 여부를 본문에 끼워 넣어도 서버에서 결정한 값을 바꿀 수 없습니다.
    void neverAcceptsOwnerOrDefaultFromBody() throws Exception {
        create(buyer.getId());
        String body = BODY.stripTrailing().replace("}", ",\"accountId\":\"" + other.getId() + "\",\"defaultAddress\":true}");
        perform(buyer.getId(), "POST", "", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(false));
        assertThat(addresses.countByAccountId(buyer.getId())).isEqualTo(2);
        assertThat(addresses.countByAccountId(other.getId())).isZero();
    }

    @Test
    // 동시에 첫 등록이 들어와도 기본 배송지는 하나만 생성합니다.
    void concurrentFirstRegistrationsKeepSingleDefault() throws Exception {
        together(() -> create(buyer.getId()), () -> create(buyer.getId()));
        assertThat(addresses.countByAccountId(buyer.getId())).isEqualTo(2);
        assertThat(defaultCount()).isEqualTo(1);
    }

    @Test
    // 다른 주소를 동시에 기본 지정해도 마지막으로 처리된 주소 하나만 남습니다.
    void concurrentDefaultChangesKeepSingleDefault() throws Exception {
        String first = create(buyer.getId()), second = create(buyer.getId());
        Callable<String> a = () -> { perform(buyer.getId(), "PUT", "/" + first + "/default", null)
                .andExpect(status().isNoContent()); return first; };
        Callable<String> b = () -> { perform(buyer.getId(), "PUT", "/" + second + "/default", null)
                .andExpect(status().isNoContent()); return second; };
        together(a, b);
        assertThat(defaultCount()).isEqualTo(1);
    }

    private Account createAccount(String email) {
        var account = Account.registerBuyer(email, "not-used-by-this-test", "구매자");
        account.initializeSignup(Instant.now(), Set.of(), false, false, false, Instant.now());
        return accounts.saveAndFlush(account);
    }
    private String create(UUID owner) throws Exception {
        var result = perform(owner, "POST", "", BODY).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse();
        return JsonPath.read(result.getContentAsString(), "$.addressId");
    }
    private ResultActions getOne(UUID owner, String id) throws Exception {
        return mvc.perform(get(BASE + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))));
    }
    private ResultActions perform(UUID owner, String method, String suffix, String body) throws Exception {
        var request = switch (method) {
            case "POST" -> post(BASE + suffix);
            case "PUT" -> put(BASE + suffix);
            default -> delete(BASE + suffix);
        };
        request.with(jwt().jwt(j -> j.subject(owner.toString()).claim("roles", List.of("BUYER"))));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    private long defaultCount() {
        return jdbc.queryForObject("select count(*) from account.addresses where account_id=? and is_default=true", Long.class, buyer.getId());
    }
    // 같은 계정의 경합을 만들고 두 요청의 예외까지 호출 스레드에 전달합니다.
    private void together(Callable<String> a, Callable<String> b) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = executor.submit(() -> { gate.await(); return a.call(); });
            var second = executor.submit(() -> { gate.await(); return b.call(); });
            gate.countDown();
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
    }
}
