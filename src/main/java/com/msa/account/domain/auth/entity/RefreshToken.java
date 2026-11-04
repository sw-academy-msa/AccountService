package com.msa.account.domain.auth.entity;

import lombok.Builder;

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
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder
    public RefreshToken(String hash, AuthSession session, Instant now) {
        this.hash = hash; this.session = session; this.issuedAt = now;
    }
    // 세션 DB 잠금 안에서만 소비하여 동시에 두 번 재발급하지 못하게 합니다.
    public boolean isConsumed() { return consumedAt != null; }
    public void consume(Instant now) { this.consumedAt = now; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class RefreshTokenBuilder {
        @Override
        public String toString() {
            return "RefreshTokenBuilder[REDACTED]";
        }
    }
}
