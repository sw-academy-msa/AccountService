package com.msa.account.domain.auth.command.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** 내부 client_credentials 응답입니다. Refresh Token·쿠키·사용자 역할을 발급하지 않습니다. */
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
}
