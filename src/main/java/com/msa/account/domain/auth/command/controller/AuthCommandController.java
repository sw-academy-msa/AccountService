package com.msa.account.domain.auth.command.controller;

import com.msa.account.domain.auth.command.dto.request.LoginRequest;
import com.msa.account.domain.auth.command.dto.response.TokenResponse;
import com.msa.account.domain.auth.command.service.AuthCommandService;
import com.msa.account.global.config.security.AuthProperties;
import com.msa.account.domain.auth.exception.AuthException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

/** 토큰 원문이 URL이나 Refresh Token JSON 응답에 노출되지 않게 HTTP 경계를 관리합니다. */
@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
public class AuthCommandController {
    private final AuthCommandService service;
    private final RefreshTokenCookie cookie;
    private final AuthProperties properties;
    public AuthCommandController(AuthCommandService service, RefreshTokenCookie cookie, AuthProperties properties) {
        this.service = service; this.cookie = cookie; this.properties = properties;
    }

    @PostMapping("/login")
    @Operation(summary = "이메일 로그인", description = "CSRF 헤더가 필요합니다. Access JWT는 응답, Refresh Token은 HttpOnly 쿠키로 전달합니다.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var tokens = service.login(request);
        cookie.write(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.access());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access JWT 재발급", description = "Refresh 쿠키와 CSRF 헤더를 전송합니다. 이전 Refresh Token은 사용할 수 없습니다.")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            var tokens = service.refresh(readCookie(request));
            cookie.write(response, tokens.refreshToken(), tokens.refreshExpiresAt());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.access());
        } catch (AuthException exception) {
            cookie.clear(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "현재 로그인 세션 종료", description = "Refresh Token을 폐기합니다. 이미 발급한 Access JWT는 만료 시각까지 유효합니다.")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(readCookie(request));
        cookie.clear(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    // 요청 본문이나 Authorization 헤더의 Refresh Token은 받지 않습니다.
    private String readCookie(HttpServletRequest request) {
        return request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(value -> value.getName().equals(properties.refreshCookieName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
}
