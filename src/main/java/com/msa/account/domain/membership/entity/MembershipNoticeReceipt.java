package com.msa.account.domain.membership.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** 만료 예정 알림을 납부기간마다 한 번만 만들기 위한 기록입니다. */
@Entity
@Table(
        name = "membership_notice_receipts",
        schema = "account",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_membership_notice_receipts_cycle",
                        columnNames = {
                            "subscriptionId",
                            "generation",
                            "cycleNumber",
                            "noticeType"
                        }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipNoticeReceipt {
    @Id private UUID id;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private long generation;

    @Column(nullable = false)
    private long cycleNumber;

    @Column(nullable = false, length = 40)
    private String noticeType;

    @Column(nullable = false)
    private Instant scheduledFor;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public MembershipNoticeReceipt(
            UUID id,
            UUID subscriptionId,
            long generation,
            long cycleNumber,
            String noticeType,
            Instant scheduledFor,
            Instant createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.generation = generation;
        this.cycleNumber = cycleNumber;
        this.noticeType = noticeType;
        this.scheduledFor = scheduledFor;
        this.createdAt = createdAt;
    }

    /** 빌더 입력값을 로그에 노출하지 않습니다. */
    public static class MembershipNoticeReceiptBuilder {
        @Override
        public String toString() {
            return "MembershipNoticeReceiptBuilder[REDACTED]";
        }
    }
}
