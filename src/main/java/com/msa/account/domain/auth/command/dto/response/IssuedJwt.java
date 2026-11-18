package com.msa.account.domain.auth.command.dto.response;

import java.time.Instant;

/** HTTP 응답이 아닌 공통 서명 결과입니다. 사용자·서비스 발급이 같은 인코더/키 경로를 재사용합니다. */
public record IssuedJwt(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedJwt[REDACTED]";
    }
}
