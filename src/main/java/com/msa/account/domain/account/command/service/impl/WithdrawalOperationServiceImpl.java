package com.msa.account.domain.account.command.service.impl;

import com.msa.account.domain.account.command.dto.response.WithdrawalLeaseResponse;
import com.msa.account.domain.account.command.service.WithdrawalOperationService;
import com.msa.account.domain.account.entity.WithdrawalOperation;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.account.repository.WithdrawalOperationRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.util.*;

/** 120초 준비 임대가 만료되면 성공을 추정하지 않고 차단을 회수합니다. 해제 전에는 새 작업을 시작하지 않습니다. */
@Service
@Transactional
public class WithdrawalOperationServiceImpl implements WithdrawalOperationService {
    private final WithdrawalOperationRepository operations;
    private final Clock clock;

    public WithdrawalOperationServiceImpl(WithdrawalOperationRepository operations, Clock clock) {
        this.operations = operations;
        this.clock = clock;
    }

    public Optional<WithdrawalLeaseResponse> begin(UUID account) {
        operations.lockCreation(account.toString());
        var row = operations.lock(account).orElse(null);
        if (row != null && !"ABORTED".equals(row.getState())) return Optional.empty();
        if (row == null)
            row = WithdrawalOperation.builder().accountId(account).now(clock.instant()).build();
        else row.restart(clock.instant());
        row = operations.saveAndFlush(row);
        return Optional.of(dto(row));
    }

    public void renew(WithdrawalLeaseResponse lease) {
        required(lease, "CHECKING").renew(clock.instant());
    }

    public void clear(WithdrawalLeaseResponse lease) {
        required(lease, "CHECKING").cleared();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(UUID account, UUID operation) {
        required(new WithdrawalLeaseResponse(account, operation), "CLEARED").committed();
    }

    public boolean abort(WithdrawalLeaseResponse lease, boolean expiredOnly) {
        var row = operations.lock(lease.accountId()).orElse(null);
        if (row == null
                || !row.getOperationId().equals(lease.operationId())
                || "COMMITTED".equals(row.getState())
                || "ABORTED".equals(row.getState())) return false;
        if (!"ABORTING".equals(row.getState())
                && expiredOnly
                && clock.instant().isBefore(row.getValidUntil())) return false;
        row.abort(clock.instant());
        return true;
    }

    public void released(WithdrawalLeaseResponse lease) {
        operations
                .lock(lease.accountId())
                .filter(
                        w ->
                                w.getOperationId().equals(lease.operationId())
                                        && "ABORTING".equals(w.getState()))
                .ifPresent(WithdrawalOperation::released);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalLeaseResponse> due() {
        return operations.due(clock.instant(), PageRequest.of(0, 25)).stream()
                .map(this::dto)
                .toList();
    }

    private WithdrawalOperation required(WithdrawalLeaseResponse lease, String state) {
        var row = operations.lock(lease.accountId()).orElseThrow(this::unavailable);
        if (!row.getOperationId().equals(lease.operationId())
                || !state.equals(row.getState())
                || !clock.instant().isBefore(row.getValidUntil())) throw unavailable();
        return row;
    }

    private WithdrawalLeaseResponse dto(WithdrawalOperation row) {
        return WithdrawalLeaseResponse.builder()
                .accountId(row.getAccountId())
                .operationId(row.getOperationId())
                .build();
    }

    private AccountException unavailable() {
        return new AccountException(AccountErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE);
    }
}
