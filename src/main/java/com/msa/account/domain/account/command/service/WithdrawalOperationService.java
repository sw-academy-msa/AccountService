package com.msa.account.domain.account.command.service;

import com.msa.account.domain.account.command.dto.response.WithdrawalLeaseResponse;

import java.util.*;

/** 네트워크 호출 없이 탈퇴 준비와 회수 상태만 짧은 트랜잭션으로 변경합니다. */
public interface WithdrawalOperationService {
    Optional<WithdrawalLeaseResponse> begin(UUID account);

    void renew(WithdrawalLeaseResponse lease);

    void clear(WithdrawalLeaseResponse lease);

    void commit(UUID account, UUID operation);

    boolean abort(WithdrawalLeaseResponse lease, boolean expiredOnly);

    void released(WithdrawalLeaseResponse lease);

    /** Payment 회원제 차단을 요청하기 전에 기록해 플래그를 꺼도 해제를 빠뜨리지 않게 합니다. */
    void fenceMembership(WithdrawalLeaseResponse lease);

    boolean membershipFenced(WithdrawalLeaseResponse lease);

    List<WithdrawalLeaseResponse> due();
}
