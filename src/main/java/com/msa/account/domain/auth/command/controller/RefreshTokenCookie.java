package com.msa.account.domain.auth.command.controller;

import com.msa.account.global.config.security.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import java.time.*;

/** Refresh Token은 JSON에 넣지 않고 HttpOnly·호스트 전용 쿠키로만 설정합니다. */
@Component
public class RefreshTokenCookie {
    private final AuthProperties properties;
    private final Clock clock;
    public RefreshTokenCookie(AuthProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }

    public void write(HttpServletResponse response, String raw, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        set(response, raw, remaining.isNegative() ? Duration.ZERO : remaining);
    }
    // 만료 쿠키도 같은 이름·경로로 내려야 브라우저가 정확히 기존 쿠키를 제거합니다.
    public void clear(HttpServletResponse response) { set(response, "", Duration.ZERO); }
    private void set(HttpServletResponse response, String value, Duration maxAge) {
        var cookie = ResponseCookie.from(properties.refreshCookieName(), value).httpOnly(true)
                .secure(properties.cookieSecure()).sameSite(properties.cookieSameSite())
                .path("/api/v1/auth").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
