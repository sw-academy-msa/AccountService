package com.msa.account.domain.verification.command.service;

import com.msa.account.domain.verification.command.dto.response.*;
import com.msa.account.domain.verification.entity.VerificationChallenge;
import java.time.Instant;
import java.util.UUID;

/** 저장소와 목적을 분리한 공통 인증 규칙입니다. 호출자가 DB 잠금과 트랜잭션을 소유합니다. */
public interface VerificationChallengeService {
    IssuedVerificationCode issue(VerificationChallenge challenge);
    EmailVerificationProofResponse confirm(VerificationChallenge challenge, UUID challengeId, String code, boolean eligible);
    Instant consume(VerificationChallenge challenge, String proof);
}
