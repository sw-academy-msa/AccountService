package com.msa.account.domain.verification.entity;

import lombok.Builder;

import jakarta.persistence.*;

/** 가입용 인증 테이블입니다. 재설정 인증은 별도 테이블에 저장해 서로 사용할 수 없습니다. */
@Entity
@Table(schema = "account", name = "email_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_verifications_challenge", columnNames = "challenge_id"))
public class EmailVerification extends VerificationChallenge {
    protected EmailVerification() { }
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder
    public EmailVerification(String email, java.time.Instant now) { super(email, now); }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class EmailVerificationBuilder {
        @Override
        public String toString() {
            return "EmailVerificationBuilder[REDACTED]";
        }
    }
}
