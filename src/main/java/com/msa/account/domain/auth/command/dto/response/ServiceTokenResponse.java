package com.msa.account.domain.auth.command.dto.response;

import lombok.Builder;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** 내부 client_credentials 응답입니다. Refresh Token·쿠키·사용자 역할을 발급하지 않습니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
public record ServiceTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("expires_at") Instant expiresAt,
        String scope) {
    @Override
    public String toString() {
        return "ServiceTokenResponse[REDACTED]";
    }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class ServiceTokenResponseBuilder {
        @Override
        public String toString() {
            return "ServiceTokenResponseBuilder[REDACTED]";
        }
    }
}
