package com.msa.account.domain.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 사용한 토큰 해시도 남겨 이전 토큰 재사용을 탐지합니다. 원문은 저장하지 않습니다. */
@Entity
@Table(schema = "account", name = "refresh_tokens", indexes = @Index(name = "ix_refresh_tokens_session", columnList = "session_id"))
public class RefreshToken {
    @Id @Column(length = 64) private String hash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuthSession session;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected RefreshToken() { }
    public RefreshToken(String hash, AuthSession session, Instant now) {
        this.hash = hash; this.session = session; this.issuedAt = now;
    }
    // 세션 DB 잠금 안에서만 소비하여 동시에 두 번 재발급하지 못하게 합니다.
    public boolean isConsumed() { return consumedAt != null; }
    public void consume(Instant now) { this.consumedAt = now; }
}
