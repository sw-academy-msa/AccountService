package com.msa.account.domain.auth.exception;

import com.msa.account.global.error.BusinessException;

/** 인증 도메인의 예상 가능한 실패를 공통 오류 응답에 연결합니다. */
public class AuthException extends BusinessException {
    public AuthException(AuthErrorCode error) { super(error); }
}
