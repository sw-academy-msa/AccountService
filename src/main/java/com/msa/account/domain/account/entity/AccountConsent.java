package com.msa.account.domain.account.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 광고성 알림에 대한 최신 동의 상태입니다. 기기 푸시 권한이나 알림 종류 설정과 구분합니다. */
@Entity
@Table(schema = "account", name = "account_consents")
public class AccountConsent {
    @Id
    private UUID id;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;
    @Column(name = "agreed_at")
    private Instant agreedAt;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountConsent() { }

    // 가입 시 미동의는 철회와 다르므로 withdrawnAt을 기록하지 않습니다.
    AccountConsent(Account account, boolean marketingAgreed, Instant now) {
        this.account = account;
        this.marketingAgreed = marketingAgreed;
        this.agreedAt = marketingAgreed ? now : null;
        this.updatedAt = now;
    }
    // 각 시각은 마지막 동의·철회 발생 시각으로 보존하며 현재 여부는 boolean으로 판단합니다.
    void change(boolean agreed, Instant now) {
        this.marketingAgreed = agreed;
        if (agreed) this.agreedAt = now;
        else this.withdrawnAt = now;
        this.updatedAt = now;
    }
    public boolean isMarketingAgreed() { return marketingAgreed; }
    public Instant getAgreedAt() { return agreedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
