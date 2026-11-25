package com.msa.account.domain.membership.dto.request;

import java.time.Instant;
import java.util.UUID;

/** Payment가 발행한 완전 자격 스냅샷입니다. 카드와 키 정보는 포함하지 않습니다. */
public record MembershipResultEvent(
        UUID eventId,
        Integer schemaVersion,
        String type,
        String source,
        UUID accountId,
        UUID subscriptionId,
        Long generation,
        Long aggregateVersion,
        Long cycleNumber,
        String state,
        Boolean autoRenew,
        Instant periodStart,
        Instant periodEnd,
        Instant entitlementUntil,
        Instant graceUntil,
        Long amount,
        String currency,
        String planVersion,
        Instant occurredAt) {
    @Override
    public String toString() {
        return "MembershipResultEvent[eventId=" + eventId + "]";
    }
}
