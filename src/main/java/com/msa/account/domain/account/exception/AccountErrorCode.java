package com.msa.account.domain.account.exception;

import com.msa.account.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 회원 도메인에서 예상할 수 있는 오류의 HTTP 상태, 식별 코드, 사용자 메시지를 정의합니다.
 */
public enum AccountErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "ACCOUNT_EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다."),
    ROLE_ALREADY_GRANTED(HttpStatus.CONFLICT, "ACCOUNT_ROLE_ALREADY_GRANTED", "이미 부여된 권한입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AccountErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    // 클라이언트에 반환할 HTTP 상태를 제공합니다.
    public HttpStatus status() {
        return status;
    }

    @Override
    // 클라이언트가 오류를 구분하는 고정 식별자를 제공합니다.
    public String code() {
        return code;
    }

    @Override
    // 사용자에게 전달할 오류 설명을 제공합니다.
    public String message() {
        return message;
    }
}
