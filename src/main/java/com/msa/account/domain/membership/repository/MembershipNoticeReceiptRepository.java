package com.msa.account.domain.membership.repository;

import com.msa.account.domain.membership.entity.MembershipNoticeReceipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 만료 예정 알림 중복을 확인합니다. */
public interface MembershipNoticeReceiptRepository
        extends JpaRepository<MembershipNoticeReceipt, UUID> {
    boolean existsBySubscriptionIdAndGenerationAndCycleNumberAndNoticeType(
            UUID subscriptionId, long generation, long cycleNumber, String noticeType);
}
