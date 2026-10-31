package com.msa.account.domain.verification.command.dto.response;

import java.time.Instant;

/** 가입할 때 제출하는 일회용 증명입니다. JWT 로그인 토큰과 용도가 다릅니다. */
public record EmailVerificationProofResponse(String verificationToken, Instant expiresAt) { }
