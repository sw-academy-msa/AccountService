package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.*;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.verification.command.dto.request.*;
import com.msa.account.domain.verification.command.dto.response.*;
import com.msa.account.domain.verification.entity.EmailVerification;
import com.msa.account.domain.verification.exception.*;
import com.msa.account.domain.verification.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** 가입 전용 저장소와 잠금을 사용하며 공통 코드·증명 검증 규칙을 재사용합니다. */
@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private final EmailVerificationRepository repository;
    private final EmailVerificationLock lock;
    private final AccountRepository accounts;
    private final VerificationChallengeService challenges;
    private final VerificationMailSender sender;
    private final Clock clock;
    public EmailVerificationServiceImpl(EmailVerificationRepository repository, EmailVerificationLock lock,
            AccountRepository accounts, VerificationChallengeService challenges, VerificationMailSender sender, Clock clock) {
        this.repository = repository; this.lock = lock; this.accounts = accounts;
        this.challenges = challenges; this.sender = sender; this.clock = clock;
    }
    @Override
    @Transactional
    public EmailVerificationResponse request(EmailVerificationRequest request) {
        String email = normalize(request.email());
        lock.acquire(email);
        if (accounts.existsByEmail(email)) throw new AccountException(AccountErrorCode.EMAIL_ALREADY_EXISTS);
        var verification = repository.findById(email).orElseGet(() -> new EmailVerification(email, clock.instant()));
        var issued = challenges.issue(verification);
        repository.saveAndFlush(verification);
        // 기존 가입 인증은 발송 실패 시 전체 발급 상태를 롤백하는 정책을 유지합니다.
        sender.send(email, issued.code(), verification.getExpiresAt());
        return issued.response();
    }
    @Override
    @Transactional(noRollbackFor = EmailVerificationException.class)
    public EmailVerificationProofResponse confirm(EmailVerificationConfirmRequest request) {
        String email = normalize(request.email()); lock.acquire(email);
        var verification = repository.findById(email)
                .orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_CHALLENGE));
        return challenges.confirm(verification, request.challengeId(), request.code(), true);
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant consume(String email, String proof) {
        lock.acquire(email);
        var verification = repository.findById(email)
                .orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_PROOF));
        return challenges.consume(verification, proof);
    }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
