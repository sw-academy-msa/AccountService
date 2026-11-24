package com.msa.account.domain.membership.exception;

import com.msa.account.global.error.BusinessException;

/** 회원제 오류를 공통 예외 처리기로 전달합니다. */
public class MembershipException extends BusinessException {
    public MembershipException(MembershipErrorCode errorCode) {
        super(errorCode);
    }
}
