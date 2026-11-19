package com.msa.account.domain.support.exception;

import com.msa.account.global.error.BusinessException;

/** 1:1 문의 도메인 오류를 공통 응답 형식으로 전달합니다. */
public class SupportException extends BusinessException {
    public SupportException(SupportErrorCode code) { super(code); }
}
