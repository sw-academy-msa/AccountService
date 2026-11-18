package com.msa.account.domain.account.entity;

import lombok.Builder;

import jakarta.persistence.*;
import java.util.UUID;

/** 회원별 선택 알림 설정입니다. 실제 발송 작업과 구독은 Notification 서비스 소유입니다. */
@Entity
@Table(schema = "account", name = "account_notification_settings")
public class AccountNotificationSettings {
    @Id
    private UUID id;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(name = "sale_start", nullable = false)
    private boolean saleStart;
    @Column(name = "personalized_recommendation", nullable = false)
    private boolean personalizedRecommendation;

    protected AccountNotificationSettings() { }

    // 별도 테이블에 저장하고 생략한 설정은 DTO에서 false로 정규화합니다.
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder(access = lombok.AccessLevel.PACKAGE)
    AccountNotificationSettings(Account account, boolean saleStart, boolean personalizedRecommendation) {
        this.account = account;
        this.saleStart = saleStart;
        this.personalizedRecommendation = personalizedRecommendation;
    }
    // 유형별 설정만 변경하며 광고 동의 상태는 별도로 유지합니다.
    void change(boolean saleStart, boolean recommendation) {
        this.saleStart = saleStart;
        this.personalizedRecommendation = recommendation;
    }
    public boolean isSaleStart() { return saleStart; }
    public boolean isPersonalizedRecommendation() { return personalizedRecommendation; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    static class AccountNotificationSettingsBuilder {
        @Override
        public String toString() {
            return "AccountNotificationSettingsBuilder[REDACTED]";
        }
    }
}
