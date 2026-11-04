package com.msa.account.domain.auth.command.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 브라우저 메모리에 둘 Access JWT만 공개합니다. Refresh Token은 쿠키로만 전달합니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
public record TokenResponse(String accessToken, String tokenType, Instant expiresAt,
        UUID accountId, Set<String> roles) {
    // 토큰 응답 객체를 로그에 전달해도 JWT 원문을 출력하지 않습니다.
    @Override public String toString() { return "TokenResponse[REDACTED]"; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class TokenResponseBuilder {
        @Override
        public String toString() {
            return "TokenResponseBuilder[REDACTED]";
        }
    }
}
