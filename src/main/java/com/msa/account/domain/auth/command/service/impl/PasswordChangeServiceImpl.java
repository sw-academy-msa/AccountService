package com.msa.account.domain.auth.command.service.impl;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.auth.command.dto.request.ChangePasswordRequest;
import com.msa.account.domain.auth.command.service.PasswordChangeService;
import com.msa.account.domain.auth.repository.AuthSessionRepository;
import com.msa.account.domain.auth.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
import java.util.UUID;
/** 비밀번호 변경과 모든 기기의 Refresh 세션 폐기를 같은 트랜잭션으로 처리합니다. */
@Service
@Transactional
public class PasswordChangeServiceImpl implements PasswordChangeService {
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public PasswordChangeServiceImpl(AccountRepository accounts, AuthSessionRepository sessions, PasswordEncoder encoder, Clock clock) {
        this.accounts = accounts; this.sessions = sessions; this.encoder = encoder; this.clock = clock;
    }
    @Override
    public void change(UUID accountId, ChangePasswordRequest request) {
        // 로그인·재발급과 동일하게 계정을 먼저 잠가 변경 후 이전 비밀번호로 세션이 생기지 않게 합니다.
        var account = accounts.findLockedById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE));
        if (account.getStatus() != AccountStatus.ACTIVE) throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        if (!encoder.matches(request.currentPassword(), account.getPasswordHash()))
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        account.changePasswordHash(encoder.encode(request.newPassword()));
        sessions.revokeAllByAccountId(accountId, clock.instant());
    }
}
