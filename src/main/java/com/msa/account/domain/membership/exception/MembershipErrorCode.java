package com.msa.account.domain.membership.exception;

import com.msa.account.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 회원제 API의 HTTP 상태, 식별 코드, 사용자 메시지를 정의합니다. */
public enum MembershipErrorCode implements ErrorCode {
    NOT_CANCELABLE(HttpStatus.CONFLICT, "MEMBERSHIP_NOT_CANCELABLE", "해지할 수 있는 멤버십이 없습니다."),
    UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MEMBERSHIP_UNAVAILABLE",
            "멤버십 기능을 지금 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    MembershipErrorCode(HttpStatus status, String code, String message) {
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
