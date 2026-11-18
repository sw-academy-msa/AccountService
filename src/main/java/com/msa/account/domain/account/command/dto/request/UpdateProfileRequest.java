package com.msa.account.domain.account.command.dto.request;
import jakarta.validation.constraints.*;
/** 이메일·권한은 수정 대상에서 제외하고 이름만 변경합니다. */
public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name) {
    public UpdateProfileRequest { if (name != null) name = name.trim(); }
}
