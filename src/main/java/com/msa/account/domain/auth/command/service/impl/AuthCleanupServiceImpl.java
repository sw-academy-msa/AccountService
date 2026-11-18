package com.msa.account.domain.auth.command.service.impl;

import com.msa.account.domain.auth.command.service.AuthCleanupService;
import com.msa.account.global.config.scheduling.AuthCleanupProperties;
import com.msa.account.domain.auth.repository.AuthSessionRepository;
import com.msa.account.domain.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

/** 배치별 로컬 트랜잭션으로 토큰과 세션을 함께 삭제하며 활성 세션에는 접근하지 않습니다. */
@Service
public class AuthCleanupServiceImpl implements AuthCleanupService {
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final AuthCleanupProperties properties;
    private final Clock clock;

    public AuthCleanupServiceImpl(AuthSessionRepository sessions, RefreshTokenRepository tokens,
            AuthCleanupProperties properties, Clock clock) {
        this.sessions = sessions; this.tokens = tokens; this.properties = properties; this.clock = clock;
    }

    @Override
    @Transactional
    // 원래 만료 시각 기준으로 계산합니다. 로그아웃해도 보존 기간 안에는 삭제하지 않습니다.
    public int deleteExpiredBatch() {
        var ids = sessions.findCleanupBatch(clock.instant().minus(properties.retention()), properties.batchSize());
        if (ids.isEmpty()) return 0;
        tokens.deleteBySessionIds(ids);
        return sessions.deleteCleanupBatch(ids);
    }
}
