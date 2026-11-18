package com.msa.account.domain.verification.command.dto.response;

import java.time.Instant;
import java.util.UUID;

/** 클라이언트 타이머용 서버 만료 시각을 반환합니다. 인증번호 원문은 반환하지 않습니다. */
public record EmailVerificationResponse(UUID challengeId, Instant expiresAt, Instant resendAvailableAt) { }
