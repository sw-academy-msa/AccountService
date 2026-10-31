package com.msa.account.domain.auth.entity;

import com.msa.account.domain.account.entity.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 로그인 한 번마다 생성하는 세션입니다. 여러 기기의 로그인은 각각 별도로 유지합니다. */
@Entity
@Table(schema = "account", name = "auth_sessions",
        indexes = @Index(name = "ix_auth_sessions_expires_at", columnList = "expires_at"))
public class AuthSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected AuthSession() { }
    public AuthSession(Account account, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.account = account; this.createdAt = now; this.expiresAt = expiresAt;
    }
    // 재발급은 최초 로그인 시 정한 만료 시간을 연장하지 않습니다.
    public boolean isActive(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public UUID getId() { return id; }
    public Account getAccount() { return account; }
    public Instant getExpiresAt() { return expiresAt; }
}
