package com.msa.account.domain.seller.command.dto.request;

import com.msa.account.domain.seller.enums.SellerCorrectionField;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** 운영자가 요청마다 UTC 마감 시각과 항목별 사유를 지정합니다. */
public record RequestSellerCorrections(
        @NotNull Instant deadline, @NotEmpty @Size(max = 6) List<@NotNull @Valid Item> items) {
    public record Item(
            @NotNull SellerCorrectionField field, @NotBlank @Size(max = 500) String reason) {
        @Override
        public String toString() {
            return "CorrectionItem[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "RequestSellerCorrections[REDACTED]";
    }
}
