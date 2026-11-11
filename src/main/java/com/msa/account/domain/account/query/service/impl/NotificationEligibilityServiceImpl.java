package com.msa.account.domain.account.query.service.impl;

import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.query.dto.response.NotificationEligibilityResponse;
import com.msa.account.domain.account.query.service.NotificationEligibilityService;
import com.msa.account.domain.account.repository.AccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 수신 목적에 따라 최신 계정 상태와 동의를 판정합니다. 일반 업무 알림과 광고성 맞춤 알림을 구분합니다. */
@Service
@Transactional(readOnly = true)
public class NotificationEligibilityServiceImpl implements NotificationEligibilityService {
    private final AccountRepository accounts;

    public NotificationEligibilityServiceImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public NotificationEligibilityResponse check(UUID accountId) {
        boolean eligible =
                accounts.findById(accountId)
                        .map(
                                a ->
                                        a.getStatus() == AccountStatus.ACTIVE
                                                && a.hasRole(Role.BUYER)
                                                && a.getNotificationSettings() != null
                                                && a.getNotificationSettings()
                                                        .isPersonalizedRecommendation()
                                                && a.getConsent() != null
                                                && a.getConsent().isMarketingAgreed())
                        .orElse(false);
        return NotificationEligibilityResponse.builder().eligible(eligible).build();
    }

    /** 심사와 주문 안내는 분석/광고 동의를 요구하지 않으며 탈퇴·정지 계정은 제외합니다. */
    public NotificationEligibilityResponse checkTransactional(UUID accountId) {
        boolean eligible =
                accounts.findById(accountId)
                        .map(a -> a.getStatus() == AccountStatus.ACTIVE && a.hasRole(Role.BUYER))
                        .orElse(false);
        return NotificationEligibilityResponse.builder().eligible(eligible).build();
    }
}
