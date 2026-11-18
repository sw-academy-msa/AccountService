package com.msa.account.domain.auth.command.service.impl;

import com.msa.account.domain.auth.command.dto.request.*;
import com.msa.account.domain.auth.command.dto.response.PasswordResetProofResponse;
import com.msa.account.domain.auth.command.service.PasswordResetService;
import com.msa.account.domain.auth.exception.*;
import com.msa.account.domain.auth.repository.AuthSessionRepository;
import com.msa.account.domain.auth.repository.LoginAttemptLock;
import com.msa.account.domain.auth.repository.LoginAttemptRepository;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.verification.command.dto.request.*;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationResponse;
import com.msa.account.domain.verification.command.service.PasswordResetVerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Locale;

/** 증명 소비·비밀번호 저장·전체 세션 폐기를 한 트랜잭션으로 처리하며 자동 로그인하지 않습니다. */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {
    private final PasswordResetVerificationService verification;
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final LoginAttemptLock loginLock;
    private final LoginAttemptRepository attempts;
    public PasswordResetServiceImpl(PasswordResetVerificationService verification, AccountRepository accounts,
            AuthSessionRepository sessions, PasswordEncoder encoder, Clock clock,
            LoginAttemptLock loginLock, LoginAttemptRepository attempts) {
        this.verification = verification; this.accounts = accounts;
        this.sessions = sessions; this.encoder = encoder; this.clock = clock;
        this.loginLock = loginLock; this.attempts = attempts;
    }
    @Override
    public EmailVerificationResponse request(PasswordResetRequest request) {
        return verification.request(new EmailVerificationRequest(request.email()));
    }
    @Override
    // 검증 서비스의 오입력 횟수 커밋을 보존하도록 여기에는 새 트랜잭션을 만들지 않습니다.
    public PasswordResetProofResponse confirm(PasswordResetConfirmRequest request) {
        var proof = verification.confirm(new EmailVerificationConfirmRequest(request.email(), request.challengeId(), request.code()));
        return new PasswordResetProofResponse(proof.verificationToken(), proof.expiresAt());
    }
    @Override
    @Transactional
    public void complete(PasswordResetCompleteRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var id = verification.consume(email, request.resetToken());
        // 로그인과 동일한 순서로 실패 횟수 잠금부터 잡아 잠금 순서 역전과 교착을 방지합니다.
        loginLock.acquire(email);
        // 로그인·재발급도 계정 잠금을 먼저 획득하므로 이전 비밀번호로 새 세션을 만드는 경합을 막습니다.
        var account = accounts.findLockedById(id).orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_RESET_TOKEN));
        if (!account.getEmail().equals(email) || account.getStatus() != AccountStatus.ACTIVE || account.getEmailVerifiedAt() == null)
            throw new AuthException(AuthErrorCode.INVALID_RESET_TOKEN);
        account.changePasswordHash(encoder.encode(request.newPassword()));
        accounts.flush();
        sessions.revokeAllByAccountId(account.getId(), clock.instant());
        // 이메일 소유 확인을 마친 복구이므로 이전 실패 횟수가 새 비밀번호 로그인을 막지 않게 합니다.
        attempts.deleteById(email);
    }
}
