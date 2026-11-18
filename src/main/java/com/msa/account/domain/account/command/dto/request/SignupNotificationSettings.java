package com.msa.account.domain.account.command.dto.request;

/**
 * 선택 알림의 사용자 설정입니다. 판매 시작 알림은 생략하면 true(기본 켜짐), 맞춤 추천은 생략하면 false로 처리합니다.
 * 판매 시작 알림은 회원이 상품별로 직접 신청한 알림에만 쓰이며, 맞춤 추천은 광고성 수신 동의와 분석 동의가 있어야 보냅니다.
 */
public record SignupNotificationSettings(Boolean saleStart, Boolean personalizedRecommendation) {
    // 개별 필드도 선택 사항입니다. 판매 시작 알림만 명시적으로 false일 때 끕니다.
    public SignupNotificationSettings {
        saleStart = !Boolean.FALSE.equals(saleStart);
        personalizedRecommendation = Boolean.TRUE.equals(personalizedRecommendation);
    }
    public static SignupNotificationSettings defaults() {
        return new SignupNotificationSettings(true, false);
    }
}
