package com.msa.account.domain.seller.query.service.impl;

import com.msa.account.domain.seller.query.dto.response.SellerProfileResponse;
import com.msa.account.domain.seller.query.service.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** 최신 SELLER 자격을 확인하고 본인 프로필만 조회합니다. 기존 승인은 쓰기 없이 호환합니다. */
@Service
@Transactional(readOnly = true)
public class SellerProfileQueryServiceImpl implements SellerProfileQueryService {
    private final SellerAccessService access;
    private final SellerProfileRepository profiles;
    private final SellerApplicationRepository applications;
    public SellerProfileQueryServiceImpl(SellerAccessService access, SellerProfileRepository profiles,
            SellerApplicationRepository applications) {
        this.access = access; this.profiles = profiles; this.applications = applications;
    }
    @Override
    public SellerProfileResponse get(UUID id) {
        access.requireSeller(id, false);
        return profiles.findById(id).map(SellerProfileResponse::from).orElseGet(() ->
                SellerProfileResponse.fromApprovedApplication(
                    applications.findFirstByAccount_IdAndStatusOrderByAttemptNumberDesc(id, SellerApplicationStatus.APPROVED)
                        .orElseThrow(() -> new SellerException(SellerErrorCode.SELLER_REQUIRED))));
    }
}
