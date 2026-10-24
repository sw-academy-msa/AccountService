package com.msa.account.domain.verification.command.service;

import com.msa.account.domain.verification.command.dto.response.VerificationEmailContent;
import java.time.Instant;
import com.msa.account.domain.verification.enums.VerificationPurpose;

/** 메일 발송과 화면 구성을 분리하여 디자인 변경이 SMTP 로직에 영향을 주지 않게 합니다. */
public interface VerificationEmailRenderer {
    default VerificationEmailContent render(String code, Instant expiresAt) {
        return render(code, expiresAt, VerificationPurpose.SIGNUP);
    }
    VerificationEmailContent render(String code, Instant expiresAt, VerificationPurpose purpose);
}
