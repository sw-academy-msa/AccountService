package com.msa.account.domain.verification.entity;

import jakarta.persistence.*;

/** 가입용 인증 테이블입니다. 재설정 인증은 별도 테이블에 저장해 서로 사용할 수 없습니다. */
@Entity
@Table(schema = "account", name = "email_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_verifications_challenge", columnNames = "challenge_id"))
public class EmailVerification extends VerificationChallenge {
    protected EmailVerification() { }
    public EmailVerification(String email, java.time.Instant now) { super(email, now); }
}
