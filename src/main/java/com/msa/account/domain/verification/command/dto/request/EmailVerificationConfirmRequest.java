package com.msa.account.domain.verification.command.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** 이메일과 요청 ID를 함께 검증하여 재발급 전 인증번호의 재사용을 막습니다. */
public record EmailVerificationConfirmRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull UUID challengeId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    public EmailVerificationConfirmRequest {
        if (email != null) email = email.trim();
    }
}
