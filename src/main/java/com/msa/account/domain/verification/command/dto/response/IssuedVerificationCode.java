package com.msa.account.domain.verification.command.dto.response;

/** 메일 발송을 위한 내부 결과이며 HTTP 응답에 반환하면 안 됩니다. */
public record IssuedVerificationCode(String code, EmailVerificationResponse response) {
    @Override public String toString() { return "IssuedVerificationCode[REDACTED]"; }
}
