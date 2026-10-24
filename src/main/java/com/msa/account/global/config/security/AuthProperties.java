package com.msa.account.global.config.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.List;

/** 토큰 유효 시간·서명 키·쿠키·프론트 Origin을 관리합니다. 비밀 키 원문은 설정에 넣지 않습니다. */
@Validated
@ConfigurationProperties("account.auth")
public record AuthProperties(@NotBlank String issuer, @NotBlank String audience,
        @NotNull Duration accessTtl, @NotNull Duration refreshTtl,
        @Min(1) int maxLoginFailures, @NotNull Duration loginWindow,
        String signingKeyLocation, boolean cookieSecure, @NotBlank String cookieSameSite,
        List<String> allowedOrigins) {
    // 잘못된 만료 시간이나 안전하지 않은 SameSite=None 설정은 시작 시 거절합니다.
    public AuthProperties {
        if (accessTtl == null || refreshTtl == null || accessTtl.isZero() || accessTtl.isNegative()
                || refreshTtl.isZero() || refreshTtl.isNegative() || loginWindow == null
                || loginWindow.isZero() || loginWindow.isNegative())
            throw new IllegalArgumentException("토큰 유효 시간은 양수여야 합니다.");
        if (!List.of("Lax", "Strict", "None").contains(cookieSameSite)
                || ("None".equals(cookieSameSite) && !cookieSecure))
            throw new IllegalArgumentException("쿠키 SameSite 설정을 확인해 주세요.");
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*")))
            throw new IllegalArgumentException("허용 Origin에는 와일드카드를 사용할 수 없습니다.");
    }

    // 운영은 Secure 접두사를 사용하고 Domain을 설정하지 않아 현재 API 호스트에만 전송합니다.
    public String refreshCookieName() { return cookieSecure ? "__Secure-stockdrop_refresh" : "stockdrop_refresh"; }
    public String csrfCookieName() { return cookieSecure ? "__Host-stockdrop_csrf" : "stockdrop_csrf"; }
}
