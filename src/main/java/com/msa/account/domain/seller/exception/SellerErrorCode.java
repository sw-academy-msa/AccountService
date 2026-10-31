package com.msa.account.domain.seller.exception;
import com.msa.account.global.error.ErrorCode;
import org.springframework.http.HttpStatus;
/** 판매자 신청·서류 열람·운영자 심사의 업무 오류입니다. 개인정보는 메시지에 넣지 않습니다. */
public enum SellerErrorCode implements ErrorCode {
    BUYER_REQUIRED(HttpStatus.FORBIDDEN, "SELLER_BUYER_REQUIRED", "인증 완료된 구매자 계정이 필요합니다."),
    SELLER_REQUIRED(HttpStatus.FORBIDDEN, "SELLER_APPROVAL_REQUIRED", "승인된 판매자 계정이 필요합니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "SELLER_ADMIN_REQUIRED", "운영자 권한이 필요합니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_APPLICATION_NOT_FOUND", "신청 정보를 찾을 수 없습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "SELLER_ALREADY_APPLIED", "심사 중이거나 승인된 신청이 있습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "SELLER_ALREADY_REVIEWED", "이미 처리된 신청입니다."),
    CERTIFICATE_REQUIRED(HttpStatus.BAD_REQUEST, "SELLER_CERTIFICATE_REQUIRED", "사업자등록증 PDF 파일 1개가 필요합니다."),
    INVALID_CERTIFICATE(HttpStatus.BAD_REQUEST, "SELLER_INVALID_CERTIFICATE", "올바른 PDF 파일을 첨부해 주세요."),
    CERTIFICATE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "SELLER_CERTIFICATE_TOO_LARGE", "PDF 파일 크기가 허용 범위를 초과했습니다."),
    CERTIFICATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SELLER_CERTIFICATE_UNAVAILABLE", "서류를 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    CERTIFICATE_REVIEW_REQUIRED(HttpStatus.CONFLICT, "SELLER_CERTIFICATE_REVIEW_REQUIRED", "사업자등록증 PDF를 먼저 조회해 주세요.");
    private final HttpStatus status; private final String code; private final String message;
    SellerErrorCode(HttpStatus status, String code, String message) { this.status=status; this.code=code; this.message=message; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
