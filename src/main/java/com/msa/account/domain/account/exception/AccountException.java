package com.msa.account.domain.account.exception;

import com.msa.account.global.error.BusinessException;

/**
 * 회원 도메인 오류를 공통 예외 처리기로 전달합니다.
 */
public class AccountException extends BusinessException {

    public AccountException(AccountErrorCode errorCode) {
        super(errorCode);
    }
}
