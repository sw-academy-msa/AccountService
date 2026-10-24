package com.msa.account.domain.auth.command.dto.response;

import java.time.Instant;

/** 비밀번호 변경에만 한 번 사용할 수 있는 증명이며 로그인 토큰이 아닙니다. */
public record PasswordResetProofResponse(String resetToken, Instant expiresAt) {
    @Override public String toString() { return "PasswordResetProofResponse[REDACTED]"; }
}
