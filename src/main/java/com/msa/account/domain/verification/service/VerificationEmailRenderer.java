package com.msa.account.domain.verification.service;

import com.msa.account.domain.verification.dto.response.VerificationEmailContent;
import java.time.Instant;

/** 메일 발송과 화면 구성을 분리하여 디자인 변경이 SMTP 로직에 영향을 주지 않게 합니다. */
public interface VerificationEmailRenderer {
    VerificationEmailContent render(String code, Instant expiresAt);
}
