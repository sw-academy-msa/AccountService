package com.msa.account.domain.verification.exception;

import com.msa.account.global.error.BusinessException;

/** 인증 실패를 공통 오류 응답으로 변환하기 위한 도메인 예외입니다. */
public class EmailVerificationException extends BusinessException {
    public EmailVerificationException(EmailVerificationErrorCode code) {
        super(code);
    }
}
