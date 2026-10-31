package com.msa.account.domain.seller.query.dto.response;

import com.msa.account.domain.seller.entity.*;
import java.time.Instant;
import java.util.UUID;

/** 마이페이지의 현재 판매자 정보입니다. 계좌 원문·암호문·파일 저장 키는 반환하지 않습니다. */
public record SellerProfileResponse(UUID accountId, UUID approvedApplicationId, String sellerDisplayName,
        String contactName, String contactEmail, String contactPhone, BankAccount bankAccount,
        SellerApplicationResponse.Location dispatchAddress, SellerApplicationResponse.Location returnAddress,
        Instant approvedAt, Instant updatedAt) {
    /** 신청에서 수집하지 않은 예금주는 임의 추정하지 않고 null로 표시합니다. */
    public record BankAccount(String bankName, String maskedBankAccount, String accountHolderName) {
        @Override public String toString() { return "BankAccount[REDACTED]"; }
    }

    public static SellerProfileResponse from(SellerProfile profile) {
        var approved = profile.getApprovedApplication();
        return new SellerProfileResponse(profile.getAccountId(), approved.getId(), profile.getSellerDisplayName(),
                profile.getContactName(), profile.getContactEmail(), profile.getContactPhone(),
                new BankAccount(profile.getBankName(), profile.getMaskedBankAccount(), profile.getAccountHolderName()),
                SellerApplicationResponse.Location.from(profile.getDispatchAddress()),
                SellerApplicationResponse.Location.from(profile.getReturnAddress()), approved.getReviewedAt(), profile.getUpdatedAt());
    }

    // 기존 승인 데이터는 읽기에서 행을 생성하지 않고 승인 당시 값을 그대로 제공합니다.
    public static SellerProfileResponse fromApprovedApplication(SellerApplication approved) {
        return new SellerProfileResponse(approved.getAccountId(), approved.getId(), approved.getSellerDisplayName(),
                approved.getContactName(), approved.getContactEmail(), approved.getContactPhone(),
                new BankAccount(approved.getBankName(), approved.getMaskedBankAccount(), null),
                SellerApplicationResponse.Location.from(approved.getDispatchAddress()),
                SellerApplicationResponse.Location.from(approved.getReturnAddress()), approved.getReviewedAt(), approved.getReviewedAt());
    }
    @Override public String toString() { return "SellerProfileResponse[REDACTED]"; }
}
