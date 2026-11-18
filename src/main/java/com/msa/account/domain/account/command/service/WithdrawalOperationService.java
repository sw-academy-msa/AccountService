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

    List<WithdrawalLeaseResponse> due();
}
