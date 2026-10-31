package com.msa.account.domain.verification.command.service;

import com.msa.account.domain.verification.command.dto.request.EmailVerificationRequest;
import com.msa.account.domain.verification.command.dto.request.EmailVerificationConfirmRequest;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationResponse;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationProofResponse;
import java.time.Instant;

/** 가입 전 이메일 인증과 가입 트랜잭션 내 증명 소비 기능의 계약입니다. */
public interface EmailVerificationService {
    EmailVerificationResponse request(EmailVerificationRequest request);
    EmailVerificationProofResponse confirm(EmailVerificationConfirmRequest request);
    Instant consume(String email, String verificationToken);
}
