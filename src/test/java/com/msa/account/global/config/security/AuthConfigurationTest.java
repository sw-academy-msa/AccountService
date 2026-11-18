package com.msa.account.global.config.security;

import com.msa.account.domain.auth.command.controller.RefreshTokenCookie;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 외부 파일 키 재사용과 운영 쿠키의 보안 속성을 네트워크 없이 검증합니다. */
class AuthConfigurationTest {
    @TempDir Path directory;

    @Test
    // A/B 인스턴스 또는 재시작이 같은 키 파일을 읽으면 같은 공개키와 kid를 사용합니다.
    void usesSameMountedKeyAcrossInstances() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("test-shared-key").generate();
        var file = directory.resolve("signing.jwk");
        Files.writeString(file, key.toJSONString());
        var properties = properties(file.toUri().toString(), true, "None");
        var environment = new MockEnvironment(); environment.setActiveProfiles("prod");
        var first = new JwtConfig().accountSigningKey(properties, environment, new DefaultResourceLoader());
        var second = new JwtConfig().accountSigningKey(properties, environment, new DefaultResourceLoader());
        assertThat(first.toPublicJWK()).isEqualTo(second.toPublicJWK());
        assertThat(first.getKeyID()).isEqualTo("test-shared-key");
    }

    @Test
    // 운영에서 키 설정 없이 임시 키를 생성하지 않고 공개키만으로 서명하려는 설정도 거절합니다.
    void requiresPrivateKeyInProduction() throws Exception {
        var environment = new MockEnvironment(); environment.setActiveProfiles("prod");
        assertThatIllegalStateException().isThrownBy(() -> new JwtConfig()
                .accountSigningKey(properties("", true, "None"), environment, new DefaultResourceLoader()));
        var file = directory.resolve("public.jwk");
        Files.writeString(file, new RSAKeyGenerator(2048).keyID("public-only").generate().toPublicJWK().toJSONString());
        assertThatIllegalStateException().isThrownBy(() -> new JwtConfig().accountSigningKey(
                properties(file.toUri().toString(), true, "None"), environment, new DefaultResourceLoader()));
    }

    @Test
    // 운영용 Refresh 쿠키에는 Secure·HttpOnly·SameSite와 제한된 경로를 적용합니다.
    void securesAndClearsProductionRefreshCookie() {
        var properties = properties("", true, "None");
        var now = Instant.parse("2026-10-24T00:00:00Z");
        var cookie = new RefreshTokenCookie(properties, Clock.fixed(now, ZoneOffset.UTC));
        var response = new MockHttpServletResponse();
        cookie.write(response, "fake-test-token", now.plusSeconds(600));
        assertThat(response.getHeader("Set-Cookie")).contains("__Secure-stockdrop_refresh=", "HttpOnly", "Secure",
                "SameSite=None", "Path=/api/v1/auth").doesNotContain("Domain=");
        var cleared = new MockHttpServletResponse(); cookie.clear(cleared);
        assertThat(cleared.getHeader("Set-Cookie")).contains("Max-Age=0", "Path=/api/v1/auth");
        assertThat(properties.csrfCookieName()).startsWith("__Host-");
    }

    @Test
    // HTTPS 없는 SameSite=None을 잘못 활성화하지 못하게 합니다.
    void disallowsInsecureCrossSiteCookies() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties("", false, "None"));
    }

    private AuthProperties properties(String location, boolean secure, String sameSite) {
        return new AuthProperties("https://api.stockdrop.example", "stockdrop-api", Duration.ofMinutes(15),
                Duration.ofDays(14), 10, Duration.ofMinutes(15), location, secure, sameSite, List.of());
    }
}
