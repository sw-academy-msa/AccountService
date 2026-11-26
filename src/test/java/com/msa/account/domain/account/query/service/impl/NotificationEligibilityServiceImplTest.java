package com.msa.account.domain.account.query.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.account.domain.account.command.dto.request.SignupNotificationSettings;
import com.msa.account.domain.account.entity.*;
import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 맞춤 알림의 계정 상태, 알림 설정과 마케팅 동의가 모두 필요한지 확인합니다. */
class NotificationEligibilityServiceImplTest {
    @Test
    void claimCampaignRequiresMarketingButNotPersonalizationAndExcludesAdmins() {
        var repository = mock(AccountRepository.class);
        var account = mock(Account.class);
        var consent = mock(AccountConsent.class);
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(account));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.hasRole(Role.BUYER)).thenReturn(true);
        when(account.getConsent()).thenReturn(consent);
        when(consent.isMarketingAgreed()).thenReturn(true);
        var service = new NotificationEligibilityServiceImpl(repository);
        assertThat(service.checkClaimCampaign(id).eligible()).isTrue();
        assertThat(service.check(id).eligible()).isFalse();
        when(account.hasRole(Role.ADMIN)).thenReturn(true);
        assertThat(service.checkClaimCampaign(id).eligible()).isFalse();
        when(account.hasRole(Role.ADMIN)).thenReturn(false);
        when(consent.isMarketingAgreed()).thenReturn(false);
        assertThat(service.checkClaimCampaign(id).eligible()).isFalse();
        when(consent.isMarketingAgreed()).thenReturn(true);
        when(account.getStatus()).thenReturn(AccountStatus.SUSPENDED);
        assertThat(service.checkClaimCampaign(id).eligible()).isFalse();
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.hasRole(Role.BUYER)).thenReturn(false);
        assertThat(service.checkClaimCampaign(id).eligible()).isFalse();
        assertThat(service.checkClaimCampaign(UUID.randomUUID()).eligible()).isFalse();
    }

    @Test
    void requiresEveryCurrentEligibilityCondition() {
        var repository = mock(AccountRepository.class);
        var account = mock(Account.class);
        var settings = mock(AccountNotificationSettings.class);
        var consent = mock(AccountConsent.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(account));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.hasRole(Role.BUYER)).thenReturn(true);
        when(account.getNotificationSettings()).thenReturn(settings);
        when(account.getConsent()).thenReturn(consent);
        when(settings.isPersonalizedRecommendation()).thenReturn(true);
        when(consent.isMarketingAgreed()).thenReturn(true);
        var service = new NotificationEligibilityServiceImpl(repository);
        assertThat(service.check(id).eligible()).isTrue();
        when(account.getStatus()).thenReturn(AccountStatus.SUSPENDED);
        assertThat(service.check(id).eligible()).isFalse();
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(settings.isPersonalizedRecommendation()).thenReturn(false);
        assertThat(service.check(id).eligible()).isFalse();
        when(settings.isPersonalizedRecommendation()).thenReturn(true);
        when(consent.isMarketingAgreed()).thenReturn(false);
        assertThat(service.check(id).eligible()).isFalse();
        when(consent.isMarketingAgreed()).thenReturn(true);
        when(account.hasRole(Role.BUYER)).thenReturn(false);
        assertThat(service.check(id).eligible()).isFalse();
    }

    @Test
    void transactionalNoticeDoesNotRequireMarketingOrAnalyticsConsent() {
        var repository = mock(AccountRepository.class);
        var account = mock(Account.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(account));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.hasRole(Role.BUYER)).thenReturn(true);
        var service = new NotificationEligibilityServiceImpl(repository);
        assertThat(service.check(id).eligible()).isFalse();
        assertThat(service.checkTransactional(id).eligible()).isTrue();
        when(account.getStatus()).thenReturn(AccountStatus.WITHDRAWN);
        assertThat(service.checkTransactional(id).eligible()).isFalse();
    }

    @Test
    void saleAlertFollowsSaleStartSettingWithoutMarketingConsent() {
        var repository = mock(AccountRepository.class);
        var account = mock(Account.class);
        var settings = mock(AccountNotificationSettings.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(account));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.hasRole(Role.BUYER)).thenReturn(true);
        when(account.getNotificationSettings()).thenReturn(settings);
        when(settings.isSaleStart()).thenReturn(true);
        var service = new NotificationEligibilityServiceImpl(repository);
        assertThat(service.checkSaleAlert(id).eligible()).isTrue();
        when(settings.isSaleStart()).thenReturn(false);
        assertThat(service.checkSaleAlert(id).eligible()).isFalse();
        when(settings.isSaleStart()).thenReturn(true);
        when(account.getStatus()).thenReturn(AccountStatus.WITHDRAWN);
        assertThat(service.checkSaleAlert(id).eligible()).isFalse();
    }

    @Test
    void signupTurnsSaleStartOnUnlessExplicitlyDisabled() {
        assertThat(new SignupNotificationSettings(null, null).saleStart()).isTrue();
        assertThat(new SignupNotificationSettings(null, null).personalizedRecommendation())
                .isFalse();
        assertThat(new SignupNotificationSettings(false, true).saleStart()).isFalse();
        assertThat(SignupNotificationSettings.defaults().saleStart()).isTrue();
    }
}
