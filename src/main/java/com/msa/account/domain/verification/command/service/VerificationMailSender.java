package com.msa.account.domain.verification.command.service;

import java.time.Instant;

/** 메일 공급자를 바꾸거나 테스트에서 대체할 수 있도록 발송 계약을 분리합니다. */
public interface VerificationMailSender {
    void send(String email, String code, Instant expiresAt);
}
