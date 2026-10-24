package com.msa.account.domain.auth.command.dto.request;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
/** 로그인한 회원이 현재 비밀번호를 증명하고 새 비밀번호를 지정합니다. */
public record ChangePasswordRequest(@NotBlank @Size(max = 64) String currentPassword,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).+$")
        String newPassword) {
    // BCrypt가 처리할 수 있는 UTF-8 바이트 수를 두 입력 모두 검증합니다.
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithinByteLimit() {
        return (currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length <= 72)
                && (newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}
