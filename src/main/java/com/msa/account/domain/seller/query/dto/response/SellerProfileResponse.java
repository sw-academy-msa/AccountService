package com.msa.account.domain.seller.query.dto.response;

import lombok.Builder;

import com.msa.account.domain.seller.entity.*;
import java.time.Instant;
import java.util.UUID;

/** 마이페이지의 현재 판매자 정보입니다. 계좌 원문·암호문·파일 저장 키는 반환하지 않습니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
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
        return SellerProfileResponse.builder()
                .accountId(profile.getAccountId())
                .approvedApplicationId(approved.getId())
                .sellerDisplayName(profile.getSellerDisplayName())
                .contactName(profile.getContactName())
                .contactEmail(profile.getContactEmail())
                .contactPhone(profile.getContactPhone())
                .bankAccount(new BankAccount(profile.getBankName(), profile.getMaskedBankAccount(), profile.getAccountHolderName()))
                .dispatchAddress(SellerApplicationResponse.Location.from(profile.getDispatchAddress()))
                .returnAddress(SellerApplicationResponse.Location.from(profile.getReturnAddress()))
                .approvedAt(approved.getReviewedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    // 기존 승인 데이터는 읽기에서 행을 생성하지 않고 승인 당시 값을 그대로 제공합니다.
    public static SellerProfileResponse fromApprovedApplication(SellerApplication approved) {
        return SellerProfileResponse.builder()
                .accountId(approved.getAccountId())
                .approvedApplicationId(approved.getId())
                .sellerDisplayName(approved.getSellerDisplayName())
                .contactName(approved.getContactName())
                .contactEmail(approved.getContactEmail())
                .contactPhone(approved.getContactPhone())
                .bankAccount(new BankAccount(approved.getBankName(), approved.getMaskedBankAccount(), null))
                .dispatchAddress(SellerApplicationResponse.Location.from(approved.getDispatchAddress()))
                .returnAddress(SellerApplicationResponse.Location.from(approved.getReturnAddress()))
                .approvedAt(approved.getReviewedAt())
                .updatedAt(approved.getReviewedAt())
                .build();
    }
    @Override public String toString() { return "SellerProfileResponse[REDACTED]"; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class SellerProfileResponseBuilder {
        @Override
        public String toString() {
            return "SellerProfileResponseBuilder[REDACTED]";
        }
    }
}
