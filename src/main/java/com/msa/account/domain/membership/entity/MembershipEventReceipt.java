package com.msa.account.domain.membership.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** 결과 이벤트 수신 기록입니다. 같은 ID의 다른 본문과 같은 버전의 다른 ID를 계약 오류로 구분합니다. */
@Entity
@Table(
        name = "membership_event_receipts",
        schema = "account",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_membership_event_receipts_version",
                        columnNames = {"subscriptionId", "sourceVersion"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipEventReceipt {
    @Id private UUID eventId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private long sourceVersion;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(nullable = false)
    private Instant processedAt;

    @Builder
    public MembershipEventReceipt(
            UUID eventId,
            UUID subscriptionId,
            long sourceVersion,
            String payloadHash,
            Instant processedAt) {
        this.eventId = eventId;
        this.subscriptionId = subscriptionId;
        this.sourceVersion = sourceVersion;
        this.payloadHash = payloadHash;
        this.processedAt = processedAt;
    }

    /** 빌더 입력값을 로그에 노출하지 않습니다. */
    public static class MembershipEventReceiptBuilder {
        @Override
        public String toString() {
            return "MembershipEventReceiptBuilder[REDACTED]";
        }
    }
}
