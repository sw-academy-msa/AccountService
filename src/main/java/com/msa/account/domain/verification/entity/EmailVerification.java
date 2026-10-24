package com.msa.account.domain.verification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 이메일별 최신 인증 요청 한 건을 저장합니다. 재발급하면 이전 인증번호와 가입 증명이 무효화됩니다.
 * 인증번호와 가입 증명은 해시만 저장하며, 원문은 로그나 DB에 보관하지 않습니다.
 */
@Entity
@Table(schema = "account", name = "email_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_verifications_challenge", columnNames = "challenge_id"))
public class EmailVerification {
    @Id
    @Column(length = 320)
    private String email;
    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;
    @Column(name = "request_count", nullable = false)
    private int requestCount;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "proof_hash", length = 64)
    private String proofHash;
    @Column(name = "proof_expires_at")
    private Instant proofExpiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailVerification() { }

    // 신규 이메일은 요청 횟수를 0으로 시작하고 실제 발급에서 횟수를 증가시킵니다.
    public EmailVerification(String email, Instant now) {
        this.email = email;
        this.windowStartedAt = now;
    }

    // 동일 이메일의 요청은 서비스의 DB 잠금 안에서만 갱신합니다.
    public void issue(UUID challengeId, String codeHash, Instant now, Instant expiresAt) {
        if (!now.isBefore(windowStartedAt.plusSeconds(3600))) {
            windowStartedAt = now;
            requestCount = 0;
        }
        this.challengeId = challengeId;
        this.codeHash = codeHash;
        this.requestedAt = now;
        this.expiresAt = expiresAt;
        this.requestCount++;
        this.attempts = 0;
        this.verifiedAt = null;
        this.proofHash = null;
        this.proofExpiresAt = null;
        this.consumedAt = null;
    }

    // 잘못된 코드의 시도 횟수도 트랜잭션을 커밋하여 우회하지 못하게 합니다.
    public void recordFailure() { attempts++; }

    // 이메일 인증 성공 시 가입 전용 일회용 증명의 해시와 만료 시각을 기록합니다.
    public void verify(String proofHash, Instant now, Instant proofExpiresAt) {
        this.verifiedAt = now;
        this.proofHash = proofHash;
        this.proofExpiresAt = proofExpiresAt;
    }

    // 가입 저장과 같은 트랜잭션에서 소비하여 가입 실패 시에는 소비도 롤백됩니다.
    public void consume(Instant now) { consumedAt = now; }

    public String getEmail() { return email; }
    public UUID getChallengeId() { return challengeId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public int getRequestCount() { return requestCount; }
    public int getAttempts() { return attempts; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getProofHash() { return proofHash; }
    public Instant getProofExpiresAt() { return proofExpiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
