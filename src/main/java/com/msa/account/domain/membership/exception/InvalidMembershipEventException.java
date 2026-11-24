package com.msa.account.domain.membership.exception;

/** 재시도해도 성공하지 않는 결과 이벤트 계약 오류이며 즉시 DLT로 보냅니다. */
public class InvalidMembershipEventException extends RuntimeException {
    public InvalidMembershipEventException() {
        super("INVALID_MEMBERSHIP_EVENT", null, false, false);
    }
}
