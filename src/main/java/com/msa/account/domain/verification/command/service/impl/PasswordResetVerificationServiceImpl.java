package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.verification.command.service.*;
import com.msa.account.domain.verification.command.dto.request.*;
import com.msa.account.domain.verification.command.dto.response.*;
import com.msa.account.domain.verification.entity.PasswordResetVerification;
import com.msa.account.domain.verification.repository.*;
import com.msa.account.domain.verification.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.*;

/** 주소 존재 여부와 무관하게 같은 발급 응답을 반환하고, 유효한 계정에만 재설정 메일을 보냅니다. */
@Service
public class PasswordResetVerificationServiceImpl implements PasswordResetVerificationService {
    private final PasswordResetVerificationRepository repository;
    private final EmailVerificationLock lock;
    private final AccountRepository accounts;
    private final VerificationChallengeService challenges;
    private final PasswordResetMailDispatcher dispatcher;
    private final Clock clock;
    public PasswordResetVerificationServiceImpl(PasswordResetVerificationRepository repository, EmailVerificationLock lock,
            AccountRepository accounts, VerificationChallengeService challenges, PasswordResetMailDispatcher dispatcher, Clock clock) {
        this.repository = repository; this.lock = lock; this.accounts = accounts;
        this.challenges = challenges; this.dispatcher = dispatcher; this.clock = clock;
    }
    @Override
    @Transactional
    // 미가입 주소도 동일한 코드 생성·DB 저장·횟수 제한을 거쳐 빠른 실패로 구분하지 않습니다.
    public EmailVerificationResponse request(EmailVerificationRequest request) {
        String email = normalize(request.email());
        lock.acquireForPasswordReset(email);
        var verification = repository.findById(email).orElseGet(() -> new PasswordResetVerification(email, clock.instant()));
        var accountId = accounts.findByEmail(email).filter(account -> account.getStatus() == AccountStatus.ACTIVE
                && account.getEmailVerifiedAt() != null).map(account -> account.getId()).orElse(null);
        var issued = challenges.issue(verification);
        verification.bindAccount(accountId);
        repository.saveAndFlush(verification);
        dispatcher.dispatchAfterCommit(email, issued.code(), verification.getExpiresAt(), accountId != null);
        return issued.response();
    }
    @Override
    @Transactional(noRollbackFor = EmailVerificationException.class)
    public EmailVerificationProofResponse confirm(EmailVerificationConfirmRequest request) {
        String email = normalize(request.email());
        lock.acquireForPasswordReset(email);
        var verification = repository.findById(email)
                .orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_CHALLENGE));
        return challenges.confirm(verification, request.challengeId(), request.code(), verification.getAccountId() != null);
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID consume(String email, String proof) {
        lock.acquireForPasswordReset(email);
        var verification = repository.findById(email)
                .orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_PROOF));
        challenges.consume(verification, proof);
        if (verification.getAccountId() == null) throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_PROOF);
        return verification.getAccountId();
    }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
