package com.msa.account.domain.auth.command.dto.request;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

/** 재설정 전용 증명과 새 비밀번호입니다. 비밀번호는 가입과 같은 정책으로 검증합니다. */
public record PasswordResetCompleteRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String resetToken,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).+$",
                message = "비밀번호에는 영문·숫자·특수문자가 포함되어야 합니다.")
        String newPassword) {
    public PasswordResetCompleteRequest { if (email != null) email = email.trim(); }
    // UTF-8 멀티바이트 입력도 BCrypt 처리 한도 내에서만 허용합니다.
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithinByteLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    @Override public String toString() { return "PasswordResetCompleteRequest[REDACTED]"; }
}
