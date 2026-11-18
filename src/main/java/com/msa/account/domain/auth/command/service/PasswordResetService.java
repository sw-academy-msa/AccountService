package com.msa.account.domain.auth.command.service;

import com.msa.account.domain.auth.command.dto.request.*;
import com.msa.account.domain.auth.command.dto.response.PasswordResetProofResponse;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationResponse;

/** 이메일 확인 후 비밀번호 변경과 전체 Refresh 세션 폐기를 수행하는 계약입니다. */
public interface PasswordResetService {
    EmailVerificationResponse request(PasswordResetRequest request);
    PasswordResetProofResponse confirm(PasswordResetConfirmRequest request);
    void complete(PasswordResetCompleteRequest request);
}
