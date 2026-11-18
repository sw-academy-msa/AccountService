package com.msa.account.domain.account.repository;

import com.msa.account.domain.account.entity.WithdrawalOperation;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

/** 최초 요청 경쟁과 완료/회수 경쟁을 DB에서 직렬화합니다. 조회 모델은 사용하지 않습니다. */
public interface WithdrawalOperationRepository extends JpaRepository<WithdrawalOperation, UUID> {
    @Query(
            value = "select true from pg_advisory_xact_lock(hashtextextended(:identity,1199))",
            nativeQuery = true)
    boolean lockCreation(@Param("identity") String identity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalOperation w where w.accountId=:account")
    Optional<WithdrawalOperation> lock(@Param("account") UUID account);

    @Query(
            "select w from WithdrawalOperation w where w.state in ('CHECKING','CLEARED','ABORTING')"
                + " and w.nextAt<=:now order by w.nextAt,w.accountId")
    List<WithdrawalOperation> due(@Param("now") Instant now, Pageable page);
}
