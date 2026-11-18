package com.msa.account.domain.account.query.service;

import com.msa.account.domain.account.query.dto.response.NotificationEligibilityResponse;

import java.util.UUID;

/** 계정 상태와 현재 알림/마케팅 동의의 원본 판정은 Account가 소유합니다. */
public interface NotificationEligibilityService {
    NotificationEligibilityResponse check(UUID accountId);

    /** 거래/심사 알림이며 광고 동의와 분리합니다. */
    NotificationEligibilityResponse checkTransactional(UUID accountId);

    /** 회원이 직접 신청한 상품 알림입니다. 활성 상태의 BUYER이고 판매 시작 알림 설정이 켜져 있어야 하며, 광고 동의는 확인하지 않습니다. */
    NotificationEligibilityResponse checkSaleAlert(UUID accountId);
}
