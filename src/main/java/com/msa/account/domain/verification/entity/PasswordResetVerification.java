package com.msa.account.domain.verification.entity;

import lombok.Builder;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 가입용과 완전히 분리한 비밀번호 재설정 인증 기록입니다. 미가입 주소도 같은 제한을 적용합니다. */
@Entity
@Table(schema = "account", name = "password_reset_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_password_reset_verifications_challenge", columnNames = "challenge_id"))
public class PasswordResetVerification extends VerificationChallenge {
    // 요청 당시 확인한 계정에 묶습니다. 미가입·정지·미인증 계정은 null로 보관하고 메일을 보내지 않습니다.
    @Column(name = "account_id")
    private UUID accountId;
    protected PasswordResetVerification() { }
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder
    public PasswordResetVerification(String email, Instant now) { super(email, now); }
    public void bindAccount(UUID accountId) { this.accountId = accountId; }
    public UUID getAccountId() { return accountId; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class PasswordResetVerificationBuilder {
        @Override
        public String toString() {
            return "PasswordResetVerificationBuilder[REDACTED]";
        }
    }
}
