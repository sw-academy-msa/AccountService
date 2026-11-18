package com.msa.account.domain.seller.command.dto.request;

import jakarta.validation.constraints.*;
import java.util.Locale;

/** 판매자 표시 이름과 담당자 정보만 수정합니다. 회원 자체의 이름·이메일은 변경하지 않습니다. */
public record UpdateSellerContactRequest(
        @Size(max = 100) String sellerDisplayName,
        @NotBlank @Size(max = 100) String contactName,
        @NotBlank @Email @Size(max = 320) String contactEmail,
        @NotBlank @Pattern(regexp = "^0[0-9]{1,2}-?[0-9]{3,4}-?[0-9]{4}$") String contactPhone) {
    public UpdateSellerContactRequest {
        sellerDisplayName = trim(sellerDisplayName);
        if (sellerDisplayName != null && sellerDisplayName.isEmpty()) sellerDisplayName = null;
        contactName = trim(contactName); contactEmail = trim(contactEmail); contactPhone = trim(contactPhone);
        if (contactEmail != null) contactEmail = contactEmail.toLowerCase(Locale.ROOT);
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    @Override public String toString() { return "UpdateSellerContactRequest[REDACTED]"; }
}
