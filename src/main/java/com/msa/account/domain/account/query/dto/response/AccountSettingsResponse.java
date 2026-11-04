package com.msa.account.domain.account.query.dto.response;

import lombok.Builder;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
/** 본인 설정 조회 응답입니다. 비밀번호·토큰·다른 회원의 정보는 포함하지 않습니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
public record AccountSettingsResponse(UUID accountId, String email, String name, Set<Role> roles,
        Set<InterestCategory> interestCategories, Notifications notificationSettings, Consent marketingConsent) {
    /** 알림 종류 설정과 광고 수신 동의를 서로 분리해 표시합니다. */
    public record Notifications(boolean saleStart, boolean personalizedRecommendation) { }
    /** 마지막 동의·철회 시각이며 현재 상태는 agreed로 판단합니다. */
    public record Consent(boolean agreed, Instant agreedAt, Instant withdrawnAt, Instant updatedAt) { }
    // 기존 개발 계정에 선택 정보 행이 없어도 미선택·미동의 기본값으로 조회합니다.
    public static AccountSettingsResponse from(Account account) {
        var settings = account.getNotificationSettings();
        var consent = account.getConsent();
        return AccountSettingsResponse.builder()
                .accountId(account.getId())
                .email(account.getEmail())
                .name(account.getName())
                .roles(account.getRoles())
                .interestCategories(account.getInterestCategories())
                .notificationSettings(new Notifications(settings != null && settings.isSaleStart(),
                        settings != null && settings.isPersonalizedRecommendation()))
                .marketingConsent(consent == null ? new Consent(false, null, null, null)
                        : new Consent(consent.isMarketingAgreed(), consent.getAgreedAt(), consent.getWithdrawnAt(), consent.getUpdatedAt()))
                .build();
    }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class AccountSettingsResponseBuilder {
        @Override
        public String toString() {
            return "AccountSettingsResponseBuilder[REDACTED]";
        }
    }

    /** 완성된 응답도 빌더와 동일하게 가려 로그에 이메일과 이름이 노출되지 않도록 합니다. */
    @Override
    public String toString() {
        return "AccountSettingsResponse[REDACTED]";
    }
}
