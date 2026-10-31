package com.msa.account.domain.account.query.dto.response;

import java.util.List;

/** 프론트가 제출 가능한 카테고리와 초기 알림 정책을 확인하는 조회 응답입니다. */
public record SignupOptionsResponse(
        List<CategoryOption> categories,
        List<String> notificationTypes,
        boolean marketingConsentRequiredForPromotionalNotifications
) {
    /** 고정 저장 코드와 사용자에게 표시할 한글 이름을 구분합니다. */
    public record CategoryOption(String code, String label) { }
}
