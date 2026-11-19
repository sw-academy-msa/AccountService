package com.msa.account.domain.support.dto.request;

import com.msa.account.domain.support.entity.SupportInquiry;

import jakarta.validation.constraints.*;

import java.util.UUID;

/** 1:1 문의 요청입니다. */
public final class SupportRequests {
    private SupportRequests() {}

    public record Create(
            @NotNull SupportInquiry.Category category,
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 1000) String body,
            UUID orderId) {}

    public record Answer(@NotBlank @Size(max = 2000) String answer) {}
}
