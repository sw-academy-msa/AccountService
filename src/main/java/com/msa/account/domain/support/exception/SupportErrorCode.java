package com.msa.account.domain.support.exception;

import com.msa.account.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 1:1 문의 오류입니다. 문의 내용은 오류 응답에 넣지 않습니다. */
public enum SupportErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "SUPPORT_INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."),
    ALREADY_ANSWERED(HttpStatus.CONFLICT, "SUPPORT_INQUIRY_ALREADY_ANSWERED", "이미 답변한 문의입니다."),
    INVALID(HttpStatus.BAD_REQUEST, "SUPPORT_INQUIRY_INVALID", "문의 내용을 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SupportErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
