package com.msa.account.domain.account.command.service.impl;

import com.msa.account.domain.account.command.dto.request.WithdrawAccountRequest;
import com.msa.account.domain.account.command.dto.response.WithdrawalResponse;
import com.msa.account.domain.account.command.service.*;
import com.msa.account.domain.account.entity.AccountWithdrawal;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.account.query.dto.response.WithdrawalEligibilityResponse;
import com.msa.account.domain.account.repository.*;
import com.msa.account.domain.auth.exception.*;
import com.msa.account.domain.auth.repository.AuthSessionRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** 로컬 사전 인증 → 영속 외부 차단/확인 → 로컬 탈퇴 커밋입니다. HTTP 동안 DB 잠금을 보유하지 않습니다. */
@Service
public class AccountWithdrawalServiceImpl implements AccountWithdrawalService {
    private final AccountRepository accounts;
    private final AccountWithdrawalRepository history;
    private final AuthSessionRepository sessions;
    private final WithdrawalEligibilityService eligibility;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public AccountWithdrawalServiceImpl(
            AccountRepository accounts,
            AccountWithdrawalRepository history,
            AuthSessionRepository sessions,
            WithdrawalEligibilityService eligibility,
            PasswordEncoder passwords,
            Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.history = history;
        this.sessions = sessions;
        this.eligibility = eligibility;
        this.passwords = passwords;
        this.clock = clock;
        this.transactions =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    public WithdrawalEligibilityResponse eligibility(UUID owner) {
        var account = accounts.findById(owner).orElseThrow(this::unavailable);
        if (account.getStatus() != AccountStatus.ACTIVE) throw unavailable();
        return eligibility.inspect(owner);
    }

    public WithdrawalResponse withdraw(UUID owner, WithdrawAccountRequest request) {
        // 비밀번호 검증 전에 외부 차단을 만들지 않습니다. 이미 탈퇴한 같은 요청은 최초 이력을 반환합니다.
        WithdrawalResponse previous =
                transactions.execute(
                        status -> {
                            var account =
                                    accounts.findLockedById(owner).orElseThrow(this::unavailable);
                            if (!passwords.matches(
                                    request.currentPassword(), account.getPasswordHash()))
                                throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
                            var record = history.findById(owner);
                            if (account.getStatus() == AccountStatus.WITHDRAWN
                                    && record.isPresent())
                                return WithdrawalResponse.builder()
                                        .withdrawnAt(record.get().getWithdrawnAt())
                                        .build();
                            if (account.getStatus() != AccountStatus.ACTIVE) throw unavailable();
                            return null;
                        });
        if (previous != null) return previous;
        var checked = eligibility.acquireClearance(owner);
        if (checked == null
                || !checked.verified()
                || checked.activeOrders() == null
                || checked.activeSales() == null
                || checked.pendingSettlements() == null)
            throw new AccountException(AccountErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE);
        if (!checked.canWithdraw()
                || checked.activeOrders() != 0
                || checked.activeSales() != 0
                || checked.pendingSettlements() != 0)
            throw new AccountException(AccountErrorCode.WITHDRAWAL_BLOCKED);
        try {
            return transactions.execute(
                    status -> {
                        var account = accounts.findLockedById(owner).orElseThrow(this::unavailable);
                        // 확인 중 비밀번호나 상태가 바뀌면 과거의 인증 결과로 탈퇴하지 않습니다.
                        if (!passwords.matches(
                                request.currentPassword(), account.getPasswordHash()))
                            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
                        var record = history.findById(owner);
                        if (account.getStatus() == AccountStatus.WITHDRAWN && record.isPresent())
                            return WithdrawalResponse.builder()
                                    .withdrawnAt(record.get().getWithdrawnAt())
                                    .build();
                        if (account.getStatus() != AccountStatus.ACTIVE) throw unavailable();
                        eligibility.commitClearance(owner, checked.operationId());
                        var now = clock.instant();
                        account.withdraw(now);
                        sessions.revokeAllByAccountId(owner, now);
                        history.save(
                                AccountWithdrawal.builder()
                                        .accountId(owner)
                                        .withdrawnAt(now)
                                        .reason(request.reason())
                                        .build());
                        return WithdrawalResponse.builder().withdrawnAt(now).build();
                    });
        } catch (RuntimeException failure) {
            // 커밋 실패 후 해제도 실패하면 영속 ABORTING/만료 회수 작업이 다시 처리합니다.
            try {
                eligibility.releaseClearance(owner, checked.operationId());
            } catch (RuntimeException ignored) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("탈퇴 차단 회수 대기: type={}", ignored.getClass().getSimpleName());
            }
            throw failure;
        }
    }

    private AuthException unavailable() {
        return new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
    }
}
