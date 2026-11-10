package com.msa.account.domain.seller.query.dto.response;

import com.msa.account.domain.seller.entity.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 신청자·운영자 상세 응답입니다. 원문 계좌번호·저장소 키·PDF 바이트는 공개하지 않습니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
public record SellerApplicationResponse(
        UUID applicationId,
        UUID accountId,
        int attemptNumber,
        String sellerDisplayName,
        String contactName,
        String contactEmail,
        String contactPhone,
        String bankName,
        String maskedBankAccount,
        Location dispatchAddress,
        Location returnAddress,
        SellerApplicationStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String rejectionReason,
        boolean certificateReviewed,
        boolean certificateRecheck,
        UUID previousApplicationId) {
    /** 주소는 신청 당시 값을 반환합니다. */
    public record Location(String postalCode, String roadAddress, String detailAddress) {
        static Location from(SellerLocation a) {
            return new Location(a.getPostalCode(), a.getRoadAddress(), a.getDetailAddress());
        }
    }

    public static SellerApplicationResponse from(SellerApplication a) {
        return from(a, false);
    }

    // 운영자 상세에서만 현재 운영자의 PDF 조회 여부를 포함합니다.
    public static SellerApplicationResponse from(SellerApplication a, boolean certificateReviewed) {
        return SellerApplicationResponse.builder()
                .applicationId(a.getId())
                .accountId(a.getAccountId())
                .attemptNumber(a.getAttemptNumber())
                .sellerDisplayName(a.getSellerDisplayName())
                .contactName(a.getContactName())
                .contactEmail(a.getContactEmail())
                .contactPhone(a.getContactPhone())
                .bankName(a.getBankName())
                .maskedBankAccount(a.getMaskedBankAccount())
                .dispatchAddress(Location.from(a.getDispatchAddress()))
                .returnAddress(Location.from(a.getReturnAddress()))
                .status(a.getStatus())
                .submittedAt(a.getSubmittedAt())
                .reviewedAt(a.getReviewedAt())
                .reviewedBy(a.getReviewedBy())
                .rejectionReason(a.getRejectionReason())
                .certificateReviewed(certificateReviewed)
                .certificateRecheck(a.isCertificateRecheck())
                .previousApplicationId(a.getPreviousApplicationId())
                .build();
    }

    @Override
    public String toString() {
        return "SellerApplicationResponse[REDACTED]";
    }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class SellerApplicationResponseBuilder {
        @Override
        public String toString() {
            return "SellerApplicationResponseBuilder[REDACTED]";
        }
    }
}
