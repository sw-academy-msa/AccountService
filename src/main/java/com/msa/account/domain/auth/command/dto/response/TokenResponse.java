package com.msa.account.domain.auth.command.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 브라우저 메모리에 둘 Access JWT만 공개합니다. Refresh Token은 쿠키로만 전달합니다. */
public record TokenResponse(String accessToken, String tokenType, Instant expiresAt,
        UUID accountId, Set<String> roles) {
    // 토큰 응답 객체를 로그에 전달해도 JWT 원문을 출력하지 않습니다.
    @Override public String toString() { return "TokenResponse[REDACTED]"; }
}
