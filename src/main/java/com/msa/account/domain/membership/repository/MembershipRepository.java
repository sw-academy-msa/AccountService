package com.msa.account.domain.membership.repository;

import com.msa.account.domain.membership.entity.Membership;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

/** 계정별 자격 행의 최초 생성 경쟁과 갱신을 DB 잠금으로 직렬화합니다. */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    @Query(
            value = "select true from pg_advisory_xact_lock(hashtextextended(:identity,2311))",
            nativeQuery = true)
    boolean lockCreation(@Param("identity") String identity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.accountId=:account")
    Optional<Membership> lock(@Param("account") UUID account);

    @Query(
            "select m.accountId from Membership m where m.state='CANCEL_SCHEDULED' and"
                    + " m.entitlementUntil>:now and m.entitlementUntil<=:until and not exists"
                    + " (select r.id from MembershipNoticeReceipt r where"
                    + " r.subscriptionId=m.subscriptionId and r.generation=m.generation and"
                    + " r.cycleNumber=m.cycleNumber and r.noticeType='MEMBERSHIP_EXPIRING')"
                    + " order by m.entitlementUntil,m.accountId")
    List<UUID> findExpiring(
            @Param("now") Instant now, @Param("until") Instant until, Pageable page);
}
