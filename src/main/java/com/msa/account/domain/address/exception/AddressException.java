package com.msa.account.domain.address.exception;
import com.msa.account.global.error.BusinessException;
/** 주소 도메인 오류를 기존 공통 응답 형식으로 전달합니다. */
public class AddressException extends BusinessException {
    public AddressException(AddressErrorCode code) { super(code); }
}
