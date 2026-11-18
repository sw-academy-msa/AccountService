package com.msa.account.domain.seller.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.Locale;

/** 요청받은 항목만 제출하는 보완 DTO입니다. 가려진 기존 계좌번호를 재입력하도록 강제하지 않습니다. */
public record SellerCorrectionSubmissionRequest(
        @Size(max = 100) String sellerDisplayName,
        @Valid Contact contact,
        @Valid BankAccount bankAccount,
        @Valid SellerAddressRequest dispatchAddress,
        @Valid SellerAddressRequest returnAddress) {
    public record Contact(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^0[0-9]{1,2}-?[0-9]{3,4}-?[0-9]{4}$") String phone) {
        public Contact {
            if (name != null) name = name.trim();
            if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
            if (phone != null) phone = phone.replace("-", "").trim();
        }

        @Override
        public String toString() {
            return "Contact[REDACTED]";
        }
    }

    public record BankAccount(
            @NotBlank @Size(max = 50) String bankName,
            @NotBlank @Pattern(regexp = "[0-9]{6,30}") String number) {
        public BankAccount {
            if (bankName != null) bankName = bankName.trim();
            if (number != null) number = number.replace("-", "").trim();
        }

        @Override
        public String toString() {
            return "BankAccount[REDACTED]";
        }
    }

    public SellerCorrectionSubmissionRequest {
        if (sellerDisplayName != null) sellerDisplayName = sellerDisplayName.trim();
    }

    @Override
    public String toString() {
        return "SellerCorrectionSubmissionRequest[REDACTED]";
    }
}
