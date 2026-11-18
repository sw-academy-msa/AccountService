package com.msa.account.domain.account.query.dto.response;

import lombok.Builder;

import java.util.List;

/** 프론트가 제출 가능한 카테고리와 초기 알림 정책을 확인하는 조회 응답입니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
public record SignupOptionsResponse(
        List<CategoryOption> categories,
        List<String> notificationTypes,
        boolean marketingConsentRequiredForPromotionalNotifications
) {
    /** 고정 저장 코드와 사용자에게 표시할 한글 이름을 구분합니다. */
    public record CategoryOption(String code, String label) { }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class SignupOptionsResponseBuilder {
        @Override
        public String toString() {
            return "SignupOptionsResponseBuilder[REDACTED]";
        }
    }
}
