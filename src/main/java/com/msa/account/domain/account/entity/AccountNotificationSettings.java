package com.msa.account.domain.account.entity;

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
    AccountNotificationSettings(Account account, boolean saleStart, boolean personalizedRecommendation) {
        this.account = account;
        this.saleStart = saleStart;
        this.personalizedRecommendation = personalizedRecommendation;
    }
    public boolean isSaleStart() { return saleStart; }
    public boolean isPersonalizedRecommendation() { return personalizedRecommendation; }
}
