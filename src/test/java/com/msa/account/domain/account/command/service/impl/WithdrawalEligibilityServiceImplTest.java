package com.msa.account.domain.account.command.service.impl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 외부 확인 계약이 연결되지 않은 실제 기본 구현은 탈퇴 가능을 반환하지 않습니다. */
class WithdrawalEligibilityServiceImplTest {
    @Test
    void unavailableChecksNeverAuthorizeWithdrawal() {
        var service =
                new WithdrawalEligibilityServiceImpl(
                        org.mockito.Mockito.mock(
                                com.msa.account.domain.account.client.WithdrawalClient.class),
                        org.mockito.Mockito.mock(
                                com.msa.account.domain.account.command.service
                                        .WithdrawalOperationService.class));
        var id = UUID.randomUUID();
        assertThat(service.inspect(id).verified()).isFalse();
        assertThat(service.acquireClearance(id).canWithdraw()).isFalse();
        assertThat(service.acquireClearance(id).activeOrders()).isNull();
    }
}
