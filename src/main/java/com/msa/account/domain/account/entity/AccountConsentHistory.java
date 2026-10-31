package com.msa.account.domain.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** 광고성 수신 동의의 이력입니다. 가입 시 선택하지 않은 경우에도 초기 상태의 근거를 남깁니다. */
@Entity
@Table(schema = "account", name = "account_consent_histories")
public class AccountConsentHistory {
    @Id @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;
    @Column(nullable = false, length = 30)
    private String source;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected AccountConsentHistory() { }

    // 가입 시 초기 선택과 설정 화면에서의 실제 변경을 출처로 구분합니다.
    AccountConsentHistory(Account account, boolean marketingAgreed, Instant now) {
        this(account, marketingAgreed, now, "SIGNUP");
    }

    AccountConsentHistory(Account account, boolean marketingAgreed, Instant now, String source) {
        this.account = account;
        this.marketingAgreed = marketingAgreed;
        this.source = source;
        this.recordedAt = now;
    }
}
