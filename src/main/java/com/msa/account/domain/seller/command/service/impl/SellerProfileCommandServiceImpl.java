package com.msa.account.domain.seller.command.service.impl;

import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.command.service.*;
import com.msa.account.domain.seller.entity.SellerProfile;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** 계정부터 잠가 동시 수정과 최초 프로필 생성을 직렬화하고 신청·심사 이력은 수정하지 않습니다. */
@Service
@Transactional
public class SellerProfileCommandServiceImpl implements SellerProfileCommandService {
    private final SellerAccessService access;
    private final SellerProfileRepository profiles;
    private final SellerApplicationRepository applications;
    private final SellerBankEncryptionService encryption;
    private final Clock clock;

    public SellerProfileCommandServiceImpl(SellerAccessService access, SellerProfileRepository profiles,
            SellerApplicationRepository applications, SellerBankEncryptionService encryption, Clock clock) {
        this.access = access; this.profiles = profiles; this.applications = applications;
        this.encryption = encryption; this.clock = clock;
    }

    @Override
    public void updateContact(UUID id, UpdateSellerContactRequest request) {
        editable(id).updateContact(request, clock.instant());
    }
    @Override
    public void updateBankAccount(UUID id, UpdateSellerBankAccountRequest request) {
        var profile = editable(id);
        profile.updateBank(request, encryption.encrypt(request.bankAccountNumber()), clock.instant());
    }
    @Override
    public void updateAddresses(UUID id, UpdateSellerAddressesRequest request) {
        editable(id).updateAddresses(request, clock.instant());
    }

    // 이미 승인된 기존 계정도 첫 변경 시에만 프로필을 생성하며 조회에는 쓰기 부작용이 없습니다.
    private SellerProfile editable(UUID id) {
        var owner = access.requireSeller(id, true);
        return profiles.findById(id).orElseGet(() -> {
            var approved = applications.findFirstByAccount_IdAndStatusOrderByAttemptNumberDesc(id, SellerApplicationStatus.APPROVED)
                    .orElseThrow(() -> new SellerException(SellerErrorCode.SELLER_REQUIRED));
            return profiles.save(new SellerProfile(owner, approved, clock.instant()));
        });
    }
}
