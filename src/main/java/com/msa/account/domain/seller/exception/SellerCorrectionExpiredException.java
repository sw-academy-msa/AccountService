package com.msa.account.domain.seller.exception;

/** 기한 만료 상태는 실패 응답에도 커밋해야 하므로 일반 입력 오류와 구분합니다. */
public class SellerCorrectionExpiredException extends SellerException {
    public SellerCorrectionExpiredException() {
        super(SellerErrorCode.CORRECTION_EXPIRED);
    }
}
