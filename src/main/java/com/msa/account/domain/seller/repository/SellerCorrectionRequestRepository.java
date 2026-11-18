package com.msa.account.domain.seller.repository;

import com.msa.account.domain.seller.entity.SellerCorrectionRequest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

/** 기한 조회도 Repository의 JPQL로 한정하고 서비스에 SQL을 두지 않습니다. */
public interface SellerCorrectionRequestRepository
        extends JpaRepository<SellerCorrectionRequest, UUID> {
    Optional<SellerCorrectionRequest> findFirstByApplicationIdOrderByCreatedAtDesc(
            UUID applicationId);

    @Query(
            "select r.id from SellerCorrectionRequest r where r.state=:state and r.deadline<=:now"
                + " order by r.deadline,r.id")
    List<UUID> findExpired(
            @Param("state") SellerCorrectionRequest.State state,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("select r.accountId from SellerCorrectionRequest r where r.id=:id")
    Optional<UUID> findAccountId(@Param("id") UUID id);
}
