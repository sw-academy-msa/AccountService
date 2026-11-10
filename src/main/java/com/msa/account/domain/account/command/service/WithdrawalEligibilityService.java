package com.msa.account.domain.account.command.service;

import com.msa.account.domain.account.query.dto.response.WithdrawalEligibilityResponse;

import java.util.UUID;

/** 다른 서비스 원본 DB를 읽지 않는 탈퇴 가능 확인 경계입니다. Query의 지연된 0건으로 승인하면 안 됩니다. */
public interface WithdrawalEligibilityService {
    WithdrawalEligibilityResponse inspect(UUID accountId);

    /** 실제 연동은 확인과 탈퇴 커밋 사이 신규 거래 생성을 막는 서비스 간 계약까지 보장해야 합니다. */
    WithdrawalEligibilityResponse acquireClearance(UUID accountId);

    /** 실제 구현은 계정 탈퇴 트랜잭션 안에서 준비 작업의 유효성과 소유권을 최종 확인합니다. */
    default void commitClearance(UUID accountId, UUID operationId) {}

    /** 로컬 커밋 실패 시 해당 작업만 회수하며 다른 재시도의 차단을 건드리지 않습니다. */
    default void releaseClearance(UUID accountId, UUID operationId) {}
}
