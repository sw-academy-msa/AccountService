package com.msa.account.domain.auth.command.dto.request;

import jakarta.validation.constraints.*;

/** 로그인에서는 비밀번호 변경 규칙을 재적용하지 않고 입력한 원문으로 검증합니다. */
public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 64) String password) {
    public LoginRequest { if (email != null) email = email.trim(); }
    // DTO가 실수로 로그에 전달되더라도 비밀번호를 노출하지 않습니다.
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
    // BCrypt가 처리할 수 없는 입력을 서버 오류 대신 검증 오류로 반환합니다.
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
