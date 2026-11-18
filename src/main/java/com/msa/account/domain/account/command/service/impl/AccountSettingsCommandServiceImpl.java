package com.msa.account.domain.account.command.service.impl;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.account.command.dto.request.*;
import com.msa.account.domain.account.command.service.AccountSettingsCommandService;
import com.msa.account.domain.auth.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;
/** 같은 회원의 설정 변경을 계정 행 잠금으로 직렬화하여 상태와 동의 이력을 함께 커밋합니다. */
@Service
@Transactional
public class AccountSettingsCommandServiceImpl implements AccountSettingsCommandService {
    private final AccountRepository accounts;
    private final Clock clock;
    public AccountSettingsCommandServiceImpl(AccountRepository accounts, Clock clock) {
        this.accounts = accounts; this.clock = clock;
    }
    @Override
    public void updateProfile(UUID id, UpdateProfileRequest request) {
        activeLocked(id).changeName(request.name(), clock.instant());
    }
    @Override
    public void updateInterests(UUID id, UpdateInterestsRequest request) {
        activeLocked(id).replaceInterests(request.interestCategories(), clock.instant());
    }
    @Override
    public void updateNotifications(UUID id, UpdateNotificationsRequest request) {
        activeLocked(id).changeNotifications(request.saleStart(), request.personalizedRecommendation(), clock.instant());
    }
    @Override
    public void updateConsent(UUID id, UpdateConsentRequest request) {
        activeLocked(id).changeMarketingConsent(request.marketingConsent(), clock.instant());
    }
    // JWT 발급 후 정지·탈퇴한 계정도 변경을 막으며 다른 회원 ID를 요청으로 받지 않습니다.
    private Account activeLocked(UUID id) {
        var account = accounts.findLockedById(id)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE));
        if (account.getStatus() != AccountStatus.ACTIVE) throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        return account;
    }
}
