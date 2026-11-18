package com.msa.account.domain.verification.command.service;

import com.msa.account.domain.verification.command.dto.request.*;
import com.msa.account.domain.verification.command.dto.response.*;
import java.util.UUID;

/** 재설정용 인증만 발급·확인·소비하며 가입용 인증과 교환할 수 없습니다. */
public interface PasswordResetVerificationService {
    EmailVerificationResponse request(EmailVerificationRequest request);
    EmailVerificationProofResponse confirm(EmailVerificationConfirmRequest request);
    UUID consume(String email, String proof);
}
