package com.msa.account.global.config.security;

import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.query.controller.AdminAccessController;
import com.msa.account.domain.auth.query.service.impl.AdminAccessServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 보안 체인과 DB 자격 서비스를 연결하여 동일 토큰의 재사용 거부를 확인합니다. */
@WebMvcTest(controllers = {AdminAccessController.class, AdminAccessSecurityTest.Probe.class})
@Import({SecurityConfig.class, AdminAccessServiceImpl.class, AdminAccessController.class,
        AdminAccessSecurityTest.Probe.class, AdminAccessSecurityTest.PropertiesConfig.class})
class AdminAccessSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AccountRepository accounts;
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void rejectsExistingAdminTokenImmediatelyAfterRevocationOrSuspension() throws Exception {
        when(decoder.decode("existing-admin")).thenReturn(token("existing-admin", "ADMIN"));
        when(accounts.hasCurrentRole(id, AccountStatus.ACTIVE, Role.ADMIN)).thenReturn(true, false, false);
        mvc.perform(get("/api/v1/auth/admin-access").header("Authorization", "Bearer existing-admin"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/admin/support-inquiries").header("Authorization", "Bearer existing-admin"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/support-inquiries/any/answer").header("Authorization", "Bearer existing-admin"))
                .andExpect(status().isForbidden());
        verify(accounts, times(3)).hasCurrentRole(id, AccountStatus.ACTIVE, Role.ADMIN);
    }

    @Test
    void requiresBearerAdminRoleEvenIfCurrentDbRoleWasGranted() throws Exception {
        mvc.perform(get("/api/v1/auth/admin-access")).andExpect(status().isUnauthorized());
        when(decoder.decode("buyer")).thenReturn(token("buyer", "BUYER"));
        mvc.perform(get("/api/v1/auth/admin-access").header("Authorization", "Bearer buyer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    private Jwt token(String value, String role) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject(id.toString())
                .claim("roles", List.of(role)).build();
    }

    /** 슬라이스에서 비밀키나 DB 없이 쿠키 설정만 제공합니다. */
    @org.springframework.boot.test.context.TestConfiguration
    static class PropertiesConfig {
        @org.springframework.context.annotation.Bean
        AuthProperties authProperties() {
            return new AuthProperties("test", "api", Duration.ofMinutes(15), Duration.ofDays(14),
                    5, Duration.ofMinutes(5), null, false, "Lax", List.of());
        }
    }

    /** 문의 컨트롤러 실행 전 공통 경계의 차단 여부만 확인합니다. */
    @RestController
    static class Probe {
        @GetMapping("/api/v1/admin/support-inquiries") String list() { return "sensitive"; }
        @PutMapping("/api/v1/admin/support-inquiries/{id}/answer") String answer() { return "changed"; }
    }
}
