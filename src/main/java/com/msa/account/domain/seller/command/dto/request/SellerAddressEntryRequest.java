package com.msa.account.domain.seller.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** 계정 ID와 기본 여부를 입력에서 신뢰하지 않고 별도 기본 지정 API로 변경합니다. */
public record SellerAddressEntryRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 100) String contactName,
        @NotBlank @Pattern(regexp = "^0[0-9]{1,2}-?[0-9]{3,4}-?[0-9]{4}$") String contactPhone,
        @NotNull @Valid SellerAddressRequest address) {
    public SellerAddressEntryRequest {
        if (label != null) label = label.trim();
        if (contactName != null) contactName = contactName.trim();
        if (contactPhone != null) contactPhone = contactPhone.replace("-", "");
    }

    @Override
    public String toString() {
        return "SellerAddressEntryRequest[REDACTED]";
    }
}
