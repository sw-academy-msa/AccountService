package com.msa.account.domain.account.query.dto.response;

import lombok.Builder;

/** HTTP와 내부 반환에 쓰는 DTO입니다. verified=false이면 미확인을 0건으로 간주하지 않습니다. */
@Builder
public record WithdrawalEligibilityResponse(
        boolean verified,
        boolean canWithdraw,
        Long activeOrders,
        Long activeSales,
        Long pendingSettlements,
        @com.fasterxml.jackson.annotation.JsonIgnore java.util.UUID operationId) {
    public static WithdrawalEligibilityResponse unavailable() {
        return WithdrawalEligibilityResponse.builder().verified(false).canWithdraw(false).build();
    }
}
