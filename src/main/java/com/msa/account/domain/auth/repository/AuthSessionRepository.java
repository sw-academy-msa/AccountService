package com.msa.account.domain.auth.repository;

import com.msa.account.domain.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.time.Instant;

/** 세션 잠금은 A/B 인스턴스의 재발급·로그아웃을 동일하게 직렬화합니다. */
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findLockedById(@Param("id") UUID id);

    // 여러 인스턴스가 실행해도 서로 잠근 행은 건너뛰므로 같은 세션을 중복 정리하지 않습니다.
    @Query(value = """
            select id from account.auth_sessions
            where expires_at <= :cutoff
            order by expires_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<UUID> findCleanupBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    // 하위 Refresh Token을 먼저 삭제한 뒤, 잠금으로 선택한 세션만 제거합니다.
    @Modifying
    @Query("delete from AuthSession s where s.id in :ids")
    int deleteCleanupBatch(@Param("ids") List<UUID> ids);

    // 계정 잠금을 잡은 비밀번호 재설정 트랜잭션에서 모든 기기의 재발급 권한을 폐기합니다.
    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.account.id = :accountId and s.revokedAt is null")
    int revokeAllByAccountId(@Param("accountId") UUID accountId, @Param("now") Instant now);
}
