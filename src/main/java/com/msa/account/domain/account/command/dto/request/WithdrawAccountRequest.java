package com.msa.account.domain.account.command.dto.request;

import jakarta.validation.constraints.*;

/** 탈퇴 의사와 현재 비밀번호를 확인하며 거래 건수와 탈퇴 가능 여부는 클라이언트에게 받지 않습니다. */
public record WithdrawAccountRequest(
        @NotBlank @Size(max = 64) String currentPassword,
        @Size(max = 200) String reason,
        @NotNull @AssertTrue Boolean confirmed) {
    /** 기존 BCrypt 인증과 동일한 바이트 상한을 검증하여 긴 다중바이트 입력을 서버 오류로 처리하지 않습니다. */
    @AssertTrue
    public boolean isPasswordWithinByteLimit() {
        return currentPassword == null
                || currentPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "WithdrawAccountRequest[REDACTED]";
    }
}
