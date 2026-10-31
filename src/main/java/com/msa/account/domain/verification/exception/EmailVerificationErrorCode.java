package com.msa.account.domain.verification.exception;

import com.msa.account.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 가입 전 이메일 인증 오류를 업무별 식별 코드로 구분합니다. */
public enum EmailVerificationErrorCode implements ErrorCode {
    INVALID_CHALLENGE(HttpStatus.BAD_REQUEST, "EMAIL_INVALID_CHALLENGE", "유효한 인증 요청이 아닙니다."),
    EXPIRED(HttpStatus.BAD_REQUEST, "EMAIL_CODE_EXPIRED", "인증번호가 만료되었습니다."),
    INVALID_CODE(HttpStatus.BAD_REQUEST, "EMAIL_INVALID_CODE", "인증번호가 일치하지 않습니다."),
    ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_ATTEMPTS_EXCEEDED", "인증 시도 횟수를 초과했습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_RATE_LIMITED", "잠시 후 인증번호를 다시 요청해 주세요."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED", "이미 인증한 요청입니다."),
    INVALID_PROOF(HttpStatus.BAD_REQUEST, "EMAIL_INVALID_PROOF", "가입에 사용할 이메일 인증이 필요합니다."),
    DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED", "인증 메일을 전송하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    EmailVerificationErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
