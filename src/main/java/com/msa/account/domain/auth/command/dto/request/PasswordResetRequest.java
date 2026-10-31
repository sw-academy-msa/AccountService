package com.msa.account.domain.auth.command.dto.request;

import jakarta.validation.constraints.*;

/** 재설정 메일을 요청할 주소입니다. 가입 여부에 관계없이 동일한 응답 형식을 사용합니다. */
public record PasswordResetRequest(@NotBlank @Email @Size(max = 320) String email) {
    public PasswordResetRequest { if (email != null) email = email.trim(); }
}
