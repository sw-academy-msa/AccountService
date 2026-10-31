package com.msa.account.domain.auth.command.service.impl;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.command.dto.request.LoginRequest;
import com.msa.account.domain.auth.command.dto.response.AuthTokens;
import com.msa.account.domain.auth.command.service.*;
import com.msa.account.global.config.security.AuthProperties;
import com.msa.account.domain.auth.entity.*;
import com.msa.account.domain.auth.exception.*;
import com.msa.account.domain.auth.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;

/** Refresh Token은 해시로 영속 저장하며 같은 로그인 세션의 변경을 DB 잠금으로 보호합니다. */
@Service
public class AuthCommandServiceImpl implements AuthCommandService {
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final AccessTokenService accessTokens;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final LoginAttemptRepository attempts;
    private final LoginAttemptLock loginLock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthCommandServiceImpl(AccountRepository accounts, AuthSessionRepository sessions,
            RefreshTokenRepository tokens, AccessTokenService accessTokens, PasswordEncoder encoder,
            AuthProperties properties, Clock clock, LoginAttemptRepository attempts, LoginAttemptLock loginLock) {
        this.accounts = accounts; this.sessions = sessions; this.tokens = tokens; this.accessTokens = accessTokens;
        this.encoder = encoder; this.properties = properties; this.clock = clock;
        this.attempts = attempts; this.loginLock = loginLock;
        // 존재하지 않는 이메일도 BCrypt를 수행하여 단순한 처리 시간 차이를 줄입니다.
        this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public AuthTokens login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        loginLock.acquire(email);
        var attempt = attempts.findById(email).orElseGet(() -> new LoginAttempt(email, clock.instant()));
        if (attempt.isBlocked(clock.instant(), properties.loginWindow(), properties.maxLoginFailures()))
            throw new AuthException(AuthErrorCode.LOGIN_RATE_LIMITED);
        Account account = accounts.findIdByEmail(email).flatMap(accounts::findLockedById).orElse(null);
        if (!encoder.matches(request.password(), account == null ? dummyPasswordHash : account.getPasswordHash()) || account == null) {
            attempt.recordFailure();
            attempts.save(attempt);
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        requireActive(account);
        attempts.deleteById(email);
        var now = clock.instant();
        AuthSession session = sessions.save(new AuthSession(account, now, now.plus(properties.refreshTtl())));
        return issue(session);
    }

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    // 이미 쓴 토큰을 다시 제출하면 세션 전체를 폐기합니다. 해당 폐기는 실패 응답에도 커밋합니다.
    public AuthTokens refresh(String raw) {
        String hash = hashValidToken(raw);
        // 비밀번호 재설정과 같은 순서(계정 → 세션)로 잠가 폐기 직후 새 토큰이 살아나는 경합을 막습니다.
        UUID accountId = tokens.findAccountIdByHash(hash)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        accounts.findLockedById(accountId).orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        UUID sessionId = tokens.findSessionIdByHash(hash)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        AuthSession session = sessions.findLockedById(sessionId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        RefreshToken token = tokens.findById(hash).orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (!session.isActive(clock.instant())) throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        if (token.isConsumed()) {
            session.revoke(clock.instant());
            throw new AuthException(AuthErrorCode.REFRESH_REUSED);
        }
        if (!isActive(session.getAccount())) {
            session.revoke(clock.instant());
            throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
        token.consume(clock.instant());
        return issue(session);
    }

    @Override
    @Transactional
    // 없는 토큰이나 이미 종료한 세션의 로그아웃도 성공으로 처리합니다.
    public void logout(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return;
        tokens.findSessionIdByHash(hash(raw)).flatMap(sessions::findLockedById)
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    // 잠금 안에서 새 난수를 발급하고 해시만 DB에 저장합니다. 만료 시각은 세션의 절대 만료를 유지합니다.
    private AuthTokens issue(AuthSession session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(hash(raw), session, clock.instant()));
        return new AuthTokens(accessTokens.issue(session.getAccount()), raw, session.getExpiresAt());
    }
    private void requireActive(Account account) {
        if (!isActive(account)) throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
    }
    private boolean isActive(Account account) {
        return account.getStatus() == AccountStatus.ACTIVE && account.getEmailVerifiedAt() != null;
    }
    private String hashValidToken(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}"))
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        return hash(raw);
    }
    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
