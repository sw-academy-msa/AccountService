package com.msa.account.domain.seller.command.dto.request;

import jakarta.validation.constraints.*;

/** 수정 화면의 은행·계좌번호·예금주를 받습니다. 계좌 실명 확인이나 송금은 수행하지 않습니다. */
public record UpdateSellerBankAccountRequest(
        @NotBlank @Size(max = 50) String bankName,
        @NotBlank @Pattern(regexp = "[0-9]{6,30}") String bankAccountNumber,
        @NotBlank @Size(max = 100) String accountHolderName) {
    public UpdateSellerBankAccountRequest {
        if (bankName != null) bankName = bankName.trim();
        if (bankAccountNumber != null) bankAccountNumber = bankAccountNumber.trim().replace("-", "");
        if (accountHolderName != null) accountHolderName = accountHolderName.trim();
    }
    @Override public String toString() { return "UpdateSellerBankAccountRequest[REDACTED]"; }
}
