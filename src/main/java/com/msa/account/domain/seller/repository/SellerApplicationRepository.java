package com.msa.account.domain.seller.repository;
import com.msa.account.domain.seller.entity.SellerApplication;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
/** 회차로 최신 신청을 결정하고 운영자 목록은 페이지 단위로 조회합니다. */
public interface SellerApplicationRepository extends JpaRepository<SellerApplication,UUID> {
    // 편의 getter getAccountId()와 혼동하지 않도록 실제 연관관계 경로를 명시합니다.
    Optional<SellerApplication> findFirstByAccount_IdOrderByAttemptNumberDesc(UUID accountId);
    // 승인된 프로필이 아직 없는 기존 개발 데이터의 조회·최초 수정을 지원합니다.
    Optional<SellerApplication> findFirstByAccount_IdAndStatusOrderByAttemptNumberDesc(UUID accountId,SellerApplicationStatus status);
    List<SellerApplication> findAllByAccount_IdOrderByAttemptNumberDesc(UUID accountId);
    Optional<SellerApplication> findByIdAndAccount_Id(UUID id,UUID accountId);
    Page<SellerApplication> findAllByStatus(SellerApplicationStatus status,Pageable pageable);
    // 업무 변경은 소유자 계정 행을 먼저 잠근 뒤 신청서를 잠그는 순서를 지킵니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SellerApplication s where s.id=:id")
    Optional<SellerApplication> findLockedById(@Param("id") UUID id);
    @Query("select s.account.id from SellerApplication s where s.id=:id")
    Optional<UUID> findAccountIdById(@Param("id") UUID id);
}
