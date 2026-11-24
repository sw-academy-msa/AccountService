package com.msa.account.domain.membership.repository;

import com.msa.account.domain.membership.entity.MembershipEventReceipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 결과 이벤트의 중복과 버전 충돌을 확인합니다. */
public interface MembershipEventReceiptRepository
        extends JpaRepository<MembershipEventReceipt, UUID> {
    boolean existsBySubscriptionIdAndSourceVersion(UUID subscriptionId, long sourceVersion);
}
