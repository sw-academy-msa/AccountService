package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.EmailVerificationService;

import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.verification.command.dto.request.EmailVerificationRequest;
import com.msa.account.domain.verification.command.dto.request.EmailVerificationConfirmRequest;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationResponse;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationProofResponse;
import com.msa.account.domain.verification.config.EmailVerificationProperties;
import com.msa.account.domain.verification.entity.EmailVerification;
import com.msa.account.domain.verification.exception.*;
import com.msa.account.domain.verification.repository.*;
import com.msa.account.domain.verification.service.VerificationMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** 인증번호 발급·검증·소비를 DB에 보관하며 이메일별 잠금으로 중복 요청과 가입 경합을 제어합니다. */
@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private final EmailVerificationRepository repository;
    private final EmailVerificationLock lock;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final VerificationMailSender sender;
    private final EmailVerificationProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationServiceImpl(EmailVerificationRepository repository, EmailVerificationLock lock,
            AccountRepository accounts, PasswordEncoder encoder, VerificationMailSender sender,
            EmailVerificationProperties properties, Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.accounts = accounts;
        this.encoder = encoder;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    // 재발급은 이전 코드·증명을 폐기합니다. SMTP 실패 시 상태 변경도 함께 롤백됩니다.
    public EmailVerificationResponse request(EmailVerificationRequest request) {
        String email = normalize(request.email());
        lock.acquire(email);
        if (accounts.existsByEmail(email)) throw new AccountException(AccountErrorCode.EMAIL_ALREADY_EXISTS);
        Instant now = clock.instant();
        EmailVerification verification = repository.findById(email)
                .orElseGet(() -> new EmailVerification(email, now));
        if (verification.getRequestedAt() != null
                && (now.isBefore(verification.getRequestedAt().plus(properties.resendInterval()))
                || (now.isBefore(verification.getWindowStartedAt().plusSeconds(3600))
                && verification.getRequestCount() >= properties.maxRequestsPerHour()))) {
            throw new EmailVerificationException(EmailVerificationErrorCode.RATE_LIMITED);
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        verification.issue(UUID.randomUUID(), encoder.encode(code), now, now.plus(properties.codeTtl()));
        repository.saveAndFlush(verification);
        // 제한된 SMTP 타임아웃 안에서 발송합니다. 공급자의 실제 수신까지 보장하지는 않습니다.
        sender.send(email, code, verification.getExpiresAt());
        return new EmailVerificationResponse(verification.getChallengeId(), verification.getExpiresAt(),
                now.plus(properties.resendInterval()));
    }

    @Override
    @Transactional(noRollbackFor = EmailVerificationException.class)
    // 오입력 예외에도 실패 횟수를 영속화합니다. 검증 실패에는 가입 증명을 생성하지 않습니다.
    public EmailVerificationProofResponse confirm(EmailVerificationConfirmRequest request) {
        String email = normalize(request.email());
        lock.acquire(email);
        EmailVerification verification = repository.findById(email)
                .orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_CHALLENGE));
        Instant now = clock.instant();
        if (!verification.getChallengeId().equals(request.challengeId()))
            throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_CHALLENGE);
        if (verification.getVerifiedAt() != null)
            throw new EmailVerificationException(EmailVerificationErrorCode.ALREADY_VERIFIED);
        if (!now.isBefore(verification.getExpiresAt()))
            throw new EmailVerificationException(EmailVerificationErrorCode.EXPIRED);
        if (verification.getAttempts() >= properties.maxAttempts())
            throw new EmailVerificationException(EmailVerificationErrorCode.ATTEMPTS_EXCEEDED);
        if (!encoder.matches(request.code(), verification.getCodeHash())) {
            verification.recordFailure();
            throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_CODE);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(properties.proofTtl());
        verification.verify(hash(token), now, expiresAt);
        return new EmailVerificationProofResponse(token, expiresAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    // 가입과 원자적으로 소비합니다. 다른 이메일·만료·위조·소비한 증명은 거절합니다.
    public Instant consume(String email, String verificationToken) {
        lock.acquire(email);
        EmailVerification verification = repository.findById(email)
                .orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_PROOF));
        Instant now = clock.instant();
        if (verification.getVerifiedAt() == null || verification.getConsumedAt() != null
                || !now.isBefore(verification.getProofExpiresAt()) || verificationToken == null
                || !MessageDigest.isEqual(hash(verificationToken).getBytes(StandardCharsets.US_ASCII),
                        verification.getProofHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_PROOF);
        }
        verification.consume(now);
        return verification.getVerifiedAt();
    }

    // 이메일 비교 규칙은 가입과 동일하게 소문자·앞뒤 공백 제거를 사용합니다.
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    // 가입 증명은 충분한 난수성을 가지므로 SHA-256 해시만 저장합니다.
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
