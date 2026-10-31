package com.msa.account.domain.verification.command.service;

import java.time.Instant;
import com.msa.account.domain.verification.enums.VerificationPurpose;

/** 메일 공급자를 바꾸거나 테스트에서 대체할 수 있도록 발송 계약을 분리합니다. */
public interface VerificationMailSender {
    // 기존 가입 인증 호출은 목적을 변경하지 않고 유지합니다.
    default void send(String email, String code, Instant expiresAt) {
        send(email, code, expiresAt, VerificationPurpose.SIGNUP);
    }
    void send(String email, String code, Instant expiresAt, VerificationPurpose purpose);
}
