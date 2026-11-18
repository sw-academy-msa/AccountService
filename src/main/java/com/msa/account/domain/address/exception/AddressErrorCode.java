package com.msa.account.domain.address.exception;
import com.msa.account.global.error.ErrorCode;
import org.springframework.http.HttpStatus;
/** 배송지 소유권과 기본 배송지 정책 오류를 구분하며 개인 주소를 오류에 노출하지 않습니다. */
public enum AddressErrorCode implements ErrorCode {
    BUYER_REQUIRED(HttpStatus.FORBIDDEN, "ADDRESS_BUYER_REQUIRED", "이용 가능한 구매자 계정이 필요합니다."),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "배송지를 찾을 수 없습니다."),
    DEFAULT_CHANGE_REQUIRED(HttpStatus.CONFLICT, "ADDRESS_DEFAULT_CHANGE_REQUIRED", "다른 배송지를 기본으로 지정한 뒤 삭제해 주세요.");
    private final HttpStatus status;
    private final String code;
    private final String message;
    AddressErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
