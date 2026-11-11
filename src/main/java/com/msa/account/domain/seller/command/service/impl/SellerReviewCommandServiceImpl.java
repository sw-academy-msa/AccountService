package com.msa.account.domain.seller.command.service.impl;

import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.seller.command.service.SellerReviewCommandService;
import com.msa.account.domain.seller.entity.SellerProfile;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 모든 심사는 신청자 계정→신청서 순서로 잠가 재신청·중복 심사와의 경합을 직렬화합니다. */
@Service
@Transactional
public class SellerReviewCommandServiceImpl implements SellerReviewCommandService {
    private final com.msa.account.domain.notification.service.BusinessNoticeService notices;
    private final SellerAccessService access;
    private final SellerApplicationRepository applications;
    private final SellerCertificateReviewRepository reviews;
    private final Clock clock;
    private final SellerProfileRepository profiles;

    public SellerReviewCommandServiceImpl(
            com.msa.account.domain.notification.service.BusinessNoticeService notices,
            SellerAccessService access,
            SellerApplicationRepository applications,
            SellerCertificateReviewRepository reviews,
            Clock clock,
            SellerProfileRepository profiles) {
        this.notices = notices;
        this.access = access;
        this.applications = applications;
        this.reviews = reviews;
        this.clock = clock;
        this.profiles = profiles;
    }

    @Override
    public void approve(UUID adminId, UUID applicationId) {
        access.requireAdmin(adminId, false);
        var ownerId = applications.findAccountIdById(applicationId).orElseThrow(this::notFound);
        var owner = access.requireBuyer(ownerId, true);
        var application = applications.findLockedById(applicationId).orElseThrow(this::notFound);
        if (application.getStatus() == SellerApplicationStatus.APPROVED) return;
        if (application.getStatus() != SellerApplicationStatus.PENDING)
            throw new SellerException(SellerErrorCode.ALREADY_REVIEWED);
        if (!reviews.existsByApplicationIdAndReviewerId(applicationId, adminId))
            throw new SellerException(SellerErrorCode.CERTIFICATE_REVIEW_REQUIRED);
        // 운영자 지정과 달리 여기서는 SELLER만 추가하며 기존 BUYER를 유지합니다.
        if (!owner.hasRole(Role.SELLER)) owner.grantRole(Role.SELLER, adminId, clock.instant());
        application.approve(adminId, clock.instant());
        applications.flush();
        notices.enqueue(
                ownerId,
                "SELLER_APPROVED",
                applicationId,
                application.notificationVersion(),
                "SELLER_APPLICATION",
                applicationId,
                null,
                null);
        // 승인·권한 부여·현재 프로필 생성을 함께 커밋합니다. 재승인은 위에서 반환하므로 수정 내용을 덮어쓰지 않습니다.
        var current = profiles.findById(ownerId);
        if (application.isCertificateRecheck()) {
            current.orElseThrow(() -> new SellerException(SellerErrorCode.SELLER_REQUIRED))
                    .adoptApproval(application, clock.instant());
            return;
        }
        profiles.save(
                SellerProfile.builder()
                        .owner(owner)
                        .approved(application)
                        .now(clock.instant())
                        .build());
    }

    @Override
    public void reject(UUID adminId, UUID applicationId, String reason) {
        access.requireAdmin(adminId, false);
        var ownerId = applications.findAccountIdById(applicationId).orElseThrow(this::notFound);
        access.requireBuyer(ownerId, true);
        var application = applications.findLockedById(applicationId).orElseThrow(this::notFound);
        var previous = application.getStatus();
        application.reject(adminId, reason, clock.instant());
        if (previous == SellerApplicationStatus.REJECTED) return;
        applications.flush();
        notices.enqueue(
                ownerId,
                "SELLER_REJECTED",
                applicationId,
                application.notificationVersion(),
                "SELLER_APPLICATION",
                applicationId,
                null,
                null);
    }

    private SellerException notFound() {
        return new SellerException(SellerErrorCode.APPLICATION_NOT_FOUND);
    }
}
