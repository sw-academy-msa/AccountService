package com.msa.account.domain.auth.repository;

import com.msa.account.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** 먼저 세션 ID만 조회하고 잠금 후 토큰을 읽어 오래된 소비 상태를 사용하지 않습니다. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Query("select t.session.id from RefreshToken t where t.hash = :hash")
    Optional<UUID> findSessionIdByHash(@Param("hash") String hash);

    @Query("select t.session.account.id from RefreshToken t where t.hash = :hash")
    Optional<UUID> findAccountIdByHash(@Param("hash") String hash);

    // 세션이 보존 기간을 넘긴 경우 해당 세션의 사용·미사용 토큰 해시를 함께 정리합니다.
    @Modifying
    @Query("delete from RefreshToken t where t.session.id in :sessionIds")
    int deleteBySessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
