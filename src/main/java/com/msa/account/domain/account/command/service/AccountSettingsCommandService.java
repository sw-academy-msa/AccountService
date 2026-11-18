package com.msa.account.domain.account.command.service;
import com.msa.account.domain.account.command.dto.request.*;
import java.util.UUID;
/** 본인 프로필과 선택 설정의 변경 계약입니다. 회원 식별자는 인증 정보에서만 받습니다. */
public interface AccountSettingsCommandService {
    void updateProfile(UUID accountId, UpdateProfileRequest request);
    void updateInterests(UUID accountId, UpdateInterestsRequest request);
    void updateNotifications(UUID accountId, UpdateNotificationsRequest request);
    void updateConsent(UUID accountId, UpdateConsentRequest request);
}
