package com.msa.account.domain.account.command.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.msa.account.domain.account.enums.InterestCategory;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 이메일 인증 후 가입합니다. 관심·알림·광고성 동의는 생략할 수 있으며 휴대전화는 수집하지 않습니다.
 */
public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 320, message = "이메일은 320자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
                message = "비밀번호에는 영문, 숫자, 특수문자가 각각 하나 이상 포함되어야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        // 인증 성공 API가 발급한 일회용 증명이며 이메일과 서버에서 대조합니다.
        @NotBlank @Size(max = 100)
        String verificationToken,

        // 선택하지 않으면 행을 저장하지 않습니다. 중복 카테고리는 Set으로 제거합니다.
        @Size(max = 12)
        Set<@jakarta.validation.constraints.NotNull InterestCategory> interestCategories,

        @Valid SignupNotificationSettings notificationSettings,
        Boolean marketingConsent
) {
    // record 생성 시 실행됩니다. 비밀번호는 사용자가 입력한 값을 유지합니다.
    public SignupRequest {
        if (email != null) {
            email = email.trim();
        }
        if (name != null) {
            name = name.trim();
        }
        interestCategories = interestCategories == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(interestCategories));
        notificationSettings = notificationSettings == null
                ? SignupNotificationSettings.disabled() : notificationSettings;
        // 생략과 명시적 null도 미동의입니다. JSON 역직렬화 전에 기본값을 정합니다.
        marketingConsent = Boolean.TRUE.equals(marketingConsent);
    }
}
