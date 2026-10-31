package com.msa.account.domain.seller.query.dto.response;
import com.msa.account.domain.seller.entity.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import java.time.Instant;
import java.util.UUID;
/** 신청자·운영자 상세 응답입니다. 원문 계좌번호·저장소 키·PDF 바이트는 공개하지 않습니다. */
public record SellerApplicationResponse(UUID applicationId,UUID accountId,int attemptNumber,String sellerDisplayName,
        String contactName,String contactEmail,String contactPhone,String bankName,String maskedBankAccount,
        Location dispatchAddress,Location returnAddress,SellerApplicationStatus status,Instant submittedAt,
        Instant reviewedAt,UUID reviewedBy,String rejectionReason,boolean certificateReviewed) {
    /** 주소는 신청 당시 값을 반환합니다. */
    public record Location(String postalCode,String roadAddress,String detailAddress) {
        static Location from(SellerLocation a) { return new Location(a.getPostalCode(),a.getRoadAddress(),a.getDetailAddress()); }
    }
    public static SellerApplicationResponse from(SellerApplication a) {
        return from(a,false);
    }
    // 운영자 상세에서만 현재 운영자의 PDF 조회 여부를 포함합니다.
    public static SellerApplicationResponse from(SellerApplication a,boolean certificateReviewed) {
        return new SellerApplicationResponse(a.getId(),a.getAccountId(),a.getAttemptNumber(),a.getSellerDisplayName(),
                a.getContactName(),a.getContactEmail(),a.getContactPhone(),a.getBankName(),a.getMaskedBankAccount(),
                Location.from(a.getDispatchAddress()),Location.from(a.getReturnAddress()),a.getStatus(),a.getSubmittedAt(),
                a.getReviewedAt(),a.getReviewedBy(),a.getRejectionReason(),certificateReviewed);
    }
    @Override public String toString() { return "SellerApplicationResponse[REDACTED]"; }
}
