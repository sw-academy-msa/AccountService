package com.msa.account.domain.auth.command.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** 가입용 요청 ID와 구분되는 재설정 인증번호 확인 입력입니다. */
public record PasswordResetConfirmRequest(@NotBlank @Email @Size(max = 320) String email,
        @NotNull UUID challengeId, @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    public PasswordResetConfirmRequest { if (email != null) email = email.trim(); }
    @Override public String toString() { return "PasswordResetConfirmRequest[REDACTED]"; }
}
