package com.msa.account.domain.account.entity;

import lombok.Builder;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** 광고성 수신 동의의 이력입니다. 가입 시 선택하지 않은 경우에도 초기 상태의 근거를 남깁니다. */
@Entity
@Table(schema = "account", name = "account_consent_histories",
        // 계정의 동의 이력 컬렉션을 계정 ID로 읽습니다.
        indexes = @Index(name = "ix_account_consent_histories_account", columnList = "account_id"))
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

    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder(access = lombok.AccessLevel.PACKAGE)
    AccountConsentHistory(Account account, boolean marketingAgreed, Instant now, String source) {
        this.account = account;
        this.marketingAgreed = marketingAgreed;
        this.source = source;
        this.recordedAt = now;
    }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    static class AccountConsentHistoryBuilder {
        // 가입 이력의 기본 출처를 유지하고 설정 변경에서만 명시적으로 덮어씁니다.
        private String source = "SIGNUP";

        @Override
        public String toString() {
            return "AccountConsentHistoryBuilder[REDACTED]";
        }
    }
}
