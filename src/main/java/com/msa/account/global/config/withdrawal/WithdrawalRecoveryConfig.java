package com.msa.account.global.config.withdrawal;

import com.msa.account.domain.account.command.service.impl.WithdrawalEligibilityServiceImpl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** 네트워크 실패와 프로세스 중단으로 남은 임시 차단을 자동 회수합니다. 성공 탈퇴의 차단은 해제하지 않습니다. */
@Configuration
@ConditionalOnProperty(name = "account.withdrawal.enabled", havingValue = "true")
public class WithdrawalRecoveryConfig {
    private final WithdrawalEligibilityServiceImpl eligibility;

    public WithdrawalRecoveryConfig(WithdrawalEligibilityServiceImpl eligibility) {
        this.eligibility = eligibility;
    }

    @Scheduled(fixedDelayString = "${account.withdrawal.poll-ms:5000}")
    public void recover() {
        eligibility.recover();
    }
}
