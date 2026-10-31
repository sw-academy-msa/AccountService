package com.msa.account.domain.verification.command.dto.request;

import jakarta.validation.constraints.*;

/** 인증번호를 받을 이메일입니다. 공백을 제거한 뒤 형식을 검증합니다. */
public record EmailVerificationRequest(
        @NotBlank @Email @Size(max = 320) String email
) {
    public EmailVerificationRequest {
        if (email != null) email = email.trim();
    }
}
