package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.VerificationChallengeService;
import com.msa.account.domain.verification.command.dto.response.*;
import com.msa.account.domain.verification.entity.VerificationChallenge;
import com.msa.account.domain.verification.exception.*;
import com.msa.account.global.config.mail.EmailVerificationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** 발급 제한·코드 검증·증명 소비를 공통화합니다. 다른 목적의 테이블을 함께 조회하지 않습니다. */
@Service
public class VerificationChallengeServiceImpl implements VerificationChallengeService {
    private final PasswordEncoder encoder;
    private final EmailVerificationProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public VerificationChallengeServiceImpl(PasswordEncoder encoder, EmailVerificationProperties properties, Clock clock) {
        this.encoder = encoder; this.properties = properties; this.clock = clock;
    }
    @Override
    // 같은 이메일의 연속 요청 제한은 호출자의 DB 잠금 안에서 검사합니다.
    public IssuedVerificationCode issue(VerificationChallenge challenge) {
        Instant now = clock.instant();
        if (challenge.getRequestedAt() != null
                && (now.isBefore(challenge.getRequestedAt().plus(properties.resendInterval()))
                || (now.isBefore(challenge.getWindowStartedAt().plusSeconds(3600))
                && challenge.getRequestCount() >= properties.maxRequestsPerHour())))
            throw new EmailVerificationException(EmailVerificationErrorCode.RATE_LIMITED);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        challenge.issue(UUID.randomUUID(), encoder.encode(code), now, now.plus(properties.codeTtl()));
        return new IssuedVerificationCode(code, new EmailVerificationResponse(challenge.getChallengeId(),
                challenge.getExpiresAt(), now.plus(properties.resendInterval())));
    }
    @Override
    // eligible은 서버에서만 결정합니다. 미가입 주소에는 번호를 맞혀도 증명을 발급하지 않습니다.
    public EmailVerificationProofResponse confirm(VerificationChallenge challenge, UUID id, String code, boolean eligible) {
        Instant now = clock.instant();
        if (!challenge.getChallengeId().equals(id)) fail(EmailVerificationErrorCode.INVALID_CHALLENGE);
        if (challenge.getVerifiedAt() != null) fail(EmailVerificationErrorCode.ALREADY_VERIFIED);
        if (!now.isBefore(challenge.getExpiresAt())) fail(EmailVerificationErrorCode.EXPIRED);
        if (challenge.getAttempts() >= properties.maxAttempts()) fail(EmailVerificationErrorCode.ATTEMPTS_EXCEEDED);
        if (!encoder.matches(code, challenge.getCodeHash()) || !eligible) {
            challenge.recordFailure();
            fail(EmailVerificationErrorCode.INVALID_CODE);
        }
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = now.plus(properties.proofTtl());
        challenge.verify(hash(proof), now, expiry);
        return new EmailVerificationProofResponse(proof, expiry);
    }
    @Override
    // 업무 변경과 같은 트랜잭션에서만 호출하여 업무 실패 시 증명 소비도 롤백합니다.
    public Instant consume(VerificationChallenge challenge, String proof) {
        Instant now = clock.instant();
        if (challenge.getVerifiedAt() == null || challenge.getConsumedAt() != null
                || !now.isBefore(challenge.getProofExpiresAt()) || proof == null
                || !MessageDigest.isEqual(hash(proof).getBytes(StandardCharsets.US_ASCII),
                        challenge.getProofHash().getBytes(StandardCharsets.US_ASCII)))
            fail(EmailVerificationErrorCode.INVALID_PROOF);
        challenge.consume(now);
        return challenge.getVerifiedAt();
    }
    private void fail(EmailVerificationErrorCode error) { throw new EmailVerificationException(error); }
    // 충분한 길이의 난수 증명은 원문 대신 SHA-256만 보관합니다.
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
