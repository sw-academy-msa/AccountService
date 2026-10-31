package com.msa.account.domain.auth.exception;

import com.msa.account.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 로그인 실패와 재발급 실패를 구분하며 비밀번호와 토큰 원문을 오류에 넣지 않습니다. */
public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "AUTH_INVALID_RESET_TOKEN", "유효한 비밀번호 재설정 인증이 필요합니다."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_UNAVAILABLE", "이용 가능한 인증 완료 계정이 아닙니다."),
    LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_RATE_LIMITED", "로그인 시도가 많습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "다시 로그인해 주세요."),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REUSED", "재사용된 토큰으로 로그인 세션이 종료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
    AuthErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
