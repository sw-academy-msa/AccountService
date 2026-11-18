package com.msa.account.domain.auth.command.dto.response;

import java.time.Instant;

/** 컨트롤러 내부 전달값입니다. Refresh Token 원문이 포함되어 HTTP JSON으로 반환하면 안 됩니다. */
public record AuthTokens(TokenResponse access, String refreshToken, Instant refreshExpiresAt) {
    // 실수로 객체를 로그에 남겨도 인증정보가 노출되지 않도록 기본 record 출력을 막습니다.
    @Override public String toString() { return "AuthTokens[REDACTED]"; }
}
