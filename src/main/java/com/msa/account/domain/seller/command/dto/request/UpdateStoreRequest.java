package com.msa.account.domain.seller.command.dto.request;

import jakarta.validation.constraints.*;

/** 스토어 소개만 변경합니다. 표시 이름은 기존 담당자 변경 계약을 그대로 사용합니다. */
public record UpdateStoreRequest(@NotNull @Size(max = 40) String introduction) {
    public UpdateStoreRequest {
        if (introduction != null) introduction = introduction.trim();
    }

    @Override
    public String toString() {
        return "UpdateStoreRequest[REDACTED]";
    }
}
