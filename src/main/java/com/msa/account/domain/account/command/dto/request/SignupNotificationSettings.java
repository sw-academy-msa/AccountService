package com.msa.account.domain.account.command.dto.request;

/**
 * 선택 알림의 사용자 설정입니다. 생략하면 모두 false로 처리합니다.
 * 설정이 true여도 광고성 수신 동의와 기기 권한 없이는 푸시를 보내면 안 됩니다.
 */
public record SignupNotificationSettings(Boolean saleStart, Boolean personalizedRecommendation) {
    // 개별 필드도 선택 사항이므로 빈 객체 또는 null 값은 모두 false로 처리합니다.
    public SignupNotificationSettings {
        saleStart = Boolean.TRUE.equals(saleStart);
        personalizedRecommendation = Boolean.TRUE.equals(personalizedRecommendation);
    }
    public static SignupNotificationSettings disabled() {
        return new SignupNotificationSettings(false, false);
    }
}
