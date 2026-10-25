package com.msa.account.domain.auth.query.controller;

import com.msa.account.domain.auth.query.dto.response.*;
import com.nimbusds.jose.jwk.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.*;

/** CSRF 준비와 공개키를 제공합니다. 본인 정보 조회는 Account API로 통일합니다. */
@RestController
public class AuthQueryController {
    private final RSAKey signingKey;
    public AuthQueryController(RSAKey signingKey) { this.signingKey = signingKey; }

    @GetMapping("/api/v1/auth/csrf")
    @SecurityRequirements
    @Operation(summary = "로그인 전 CSRF 토큰 준비", description = "쿠키를 보관하고 token 값을 headerName 헤더에 넣어 로그인·재발급·로그아웃에 보냅니다.")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @GetMapping("/.well-known/jwks.json")
    @SecurityRequirements
    @Operation(summary = "JWT 검증용 공개키 조회", description = "개인키는 반환하지 않습니다. 다른 서비스의 Resource Server가 사용합니다.")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new JWKSet(signingKey.toPublicJWK()).toJSONObject());
    }

}
