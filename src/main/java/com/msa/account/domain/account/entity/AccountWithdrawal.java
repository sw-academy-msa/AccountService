package com.msa.account.domain.account.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** 탈퇴의 중복 실행과 시각 변경을 막는 Account 소유 이력입니다. 비밀번호를 저장하지 않습니다. */
@Entity
@Getter
@Table(schema = "account", name = "account_withdrawals")
public class AccountWithdrawal {
    @Id private UUID accountId;

    @Column(nullable = false, updatable = false)
    private Instant withdrawnAt;

    @Column(length = 200, updatable = false)
    private String reason;

    @Version private Long version;

    protected AccountWithdrawal() {}

    @Builder
    public AccountWithdrawal(UUID accountId, Instant withdrawnAt, String reason) {
        this.accountId = accountId;
        this.withdrawnAt = withdrawnAt;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "AccountWithdrawal[REDACTED]";
    }

    public static class AccountWithdrawalBuilder {
        @Override
        public String toString() {
            return "AccountWithdrawalBuilder[REDACTED]";
        }
    }
}
