package com.msa.account.domain.seller.exception;
import com.msa.account.global.error.BusinessException;
/** 판매자 도메인 오류를 공통 예외 응답으로 전달합니다. */
public class SellerException extends BusinessException {
    public SellerException(SellerErrorCode code) { super(code); }
}
