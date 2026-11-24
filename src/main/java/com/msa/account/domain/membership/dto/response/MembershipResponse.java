package com.msa.account.domain.membership.dto.response;

import com.msa.account.domain.membership.entity.Membership;

import lombok.Builder;

import java.time.Instant;

/** 본인 회원 자격 조회 응답입니다. 결제 식별자와 카드 정보는 포함하지 않습니다. */
@Builder
public record MembershipResponse(
        String state,
        boolean active,
        Instant entitlementUntil,
        Instant periodEnd,
        boolean autoRenew,
        long amount,
        String currency,
        long sourceVersion,
        boolean cancelRequested,
        Instant updatedAt) {
    public static MembershipResponse from(Membership m, Instant now) {
        if (m == null || "NONE".equals(m.getState()))
            return MembershipResponse.builder().state("NONE").currency("KRW").build();
        return MembershipResponse.builder()
                .state(m.getState())
                .active(m.entitled(now))
                .entitlementUntil(m.getEntitlementUntil())
                .periodEnd(m.getPeriodEnd())
                .autoRenew(m.isAutoRenew())
                .amount(m.getPriceAmount())
                .currency(m.getCurrency())
                .sourceVersion(m.getSourceVersion())
                .cancelRequested(
                        m.getCancelGeneration() != null
                                && m.getCancelGeneration() == m.getGeneration())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
