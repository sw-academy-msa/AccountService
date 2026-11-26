package com.msa.account.domain.membership.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Payment 금융 원본의 최신 자격 스냅샷과 해지 명령 버전을 계정별로 보관합니다. */
@Entity
@Table(
        name = "memberships",
        schema = "account",
        // 만료 예정 알림 작업이 해지 예약 상태와 종료 시각 범위만 찾습니다.
        indexes = @Index(name = "ix_memberships_expiry", columnList = "state,entitlementUntil"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {
    @Id private UUID accountId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private long generation;

    @Column(nullable = false, length = 32)
    private String state;

    private Instant periodStart;
    private Instant periodEnd;
    private Instant entitlementUntil;
    private Instant graceUntil;

    @Column(nullable = false)
    private boolean autoRenew;

    @Column(nullable = false, length = 40)
    private String planVersion;

    @Column(nullable = false)
    private long priceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long cycleNumber;

    // Payment aggregateVersion이며 세대가 바뀌어도 단조 증가합니다.
    @Column(nullable = false)
    private long sourceVersion;

    // 계정별 해지 명령 버전입니다. 재가입해도 줄어들지 않습니다.
    @Column(nullable = false)
    private long commandVersion;

    private UUID cancelCommandId;
    private Long cancelGeneration;

    // Payment 결과를 기다리는 해지 취소 명령 ID입니다. 결과를 받으면 비웁니다.
    private UUID cancelWithdrawCommandId;

    @Version
    @Column(nullable = false)
    private long rowVersion;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    public Membership(UUID accountId, UUID subscriptionId, Instant now) {
        this.accountId = accountId;
        this.subscriptionId = subscriptionId;
        this.state = "NONE";
        this.planVersion = "";
        this.currency = "KRW";
        this.updatedAt = now;
    }

    /** 더 높은 원본 버전의 완전 스냅샷만 반영합니다. */
    public boolean apply(
            UUID subscriptionId,
            long generation,
            long sourceVersion,
            long cycleNumber,
            String state,
            boolean autoRenew,
            Instant periodStart,
            Instant periodEnd,
            Instant entitlementUntil,
            Instant graceUntil,
            String planVersion,
            long priceAmount,
            String currency,
            Instant now) {
        if (sourceVersion <= this.sourceVersion) return false;
        this.subscriptionId = subscriptionId;
        this.generation = generation;
        this.sourceVersion = sourceVersion;
        // 해지와 탈퇴 결과는 청구 기간 번호 0으로 오므로 마지막 납부기간 번호를 유지합니다.
        if (cycleNumber > 0) this.cycleNumber = cycleNumber;
        this.state = state;
        this.autoRenew = autoRenew;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.entitlementUntil = entitlementUntil;
        this.graceUntil = graceUntil;
        this.planVersion = planVersion;
        this.priceAmount = priceAmount;
        this.currency = currency;
        this.updatedAt = now;
        return true;
    }

    /** 같은 세대의 해지는 처음 만든 명령 ID를 다시 사용합니다. */
    public UUID requestCancel(Instant now) {
        if (cancelGeneration != null && cancelGeneration == generation) return cancelCommandId;
        commandVersion = Math.addExact(commandVersion, 1);
        cancelCommandId = UUID.randomUUID();
        cancelGeneration = generation;
        cancelWithdrawCommandId = null;
        updatedAt = now;
        return cancelCommandId;
    }

    /** 결과를 기다리는 해지 취소는 처음 만든 명령 ID를 다시 사용합니다. */
    public UUID requestCancelWithdrawal(Instant now) {
        if (cancelWithdrawCommandId != null) return cancelWithdrawCommandId;
        commandVersion = Math.addExact(commandVersion, 1);
        cancelWithdrawCommandId = UUID.randomUUID();
        // 해지 취소 뒤 다시 해지하면 새 명령을 만들도록 해지 요청 표시를 지웁니다.
        cancelCommandId = null;
        cancelGeneration = null;
        updatedAt = now;
        return cancelWithdrawCommandId;
    }

    /** Payment가 해지 취소를 반영하거나 거절한 결과를 받으면 처리 중 표시를 끝냅니다. */
    public void finishCancelWithdrawal() {
        cancelWithdrawCommandId = null;
    }

    /** 혜택 종료 시각 전의 유효 상태만 회원으로 봅니다. 만료 이벤트 지연으로 혜택을 연장하지 않습니다. */
    public boolean entitled(Instant now) {
        return ("ACTIVE".equals(state) || "GRACE".equals(state) || "CANCEL_SCHEDULED".equals(state))
                && entitlementUntil != null
                && now.isBefore(entitlementUntil);
    }

    @Override
    public String toString() {
        return "Membership[REDACTED]";
    }

    /** 빌더 입력값을 로그에 노출하지 않습니다. */
    public static class MembershipBuilder {
        @Override
        public String toString() {
            return "MembershipBuilder[REDACTED]";
        }
    }
}
