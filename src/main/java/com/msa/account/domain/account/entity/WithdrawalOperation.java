package com.msa.account.domain.account.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** 외부 차단 준비와 해제 재시도를 영속화합니다. COMMITTED는 계정 탈퇴와 같은 로컬 트랜잭션입니다. */
@Entity
@Table(name = "withdrawal_operations", schema = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalOperation {
    @Id private UUID accountId;

    @Column(nullable = false)
    private UUID operationId;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private Instant validUntil;

    @Column(nullable = false)
    private Instant nextAt;

    @Builder
    public WithdrawalOperation(UUID accountId, Instant now) {
        this.accountId = accountId;
        restart(now);
    }

    public void restart(Instant now) {
        operationId = UUID.randomUUID();
        state = "CHECKING";
        validUntil = now.plusSeconds(120);
        nextAt = validUntil;
    }

    public void renew(Instant now) {
        validUntil = now.plusSeconds(120);
        nextAt = validUntil;
    }

    public void cleared() {
        state = "CLEARED";
    }

    public void committed() {
        state = "COMMITTED";
    }

    public void abort(Instant now) {
        state = "ABORTING";
        nextAt = now.plusSeconds(10);
    }

    public void released() {
        state = "ABORTED";
    }
}
