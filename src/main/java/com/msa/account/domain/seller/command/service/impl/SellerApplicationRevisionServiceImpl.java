package com.msa.account.domain.seller.command.service.impl;

import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.command.dto.response.*;
import com.msa.account.domain.seller.command.service.*;
import com.msa.account.domain.seller.entity.*;
import com.msa.account.domain.seller.enums.*;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import com.msa.account.domain.seller.storage.CertificateStorage;

import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.*;

/** 잠금 순서는 계정→신청이며 PDF와 보완 이력은 회차별로 보존합니다. 기한 경과는 즉시 판정합니다. */
@Service
@Transactional
public class SellerApplicationRevisionServiceImpl implements SellerApplicationRevisionService {
    private final com.msa.account.domain.notification.service.BusinessNoticeService notices;
    private static final Logger log =
            LoggerFactory.getLogger(SellerApplicationRevisionServiceImpl.class);
    private final SellerAccessService access;
    private final AccountRepository accounts;
    private final SellerApplicationRepository apps;
    private final SellerProfileRepository profiles;
    private final SellerCorrectionRequestRepository corrections;
    private final SellerCertificateRepository certificates;
    private final CertificateStorage storage;
    private final CertificateValidationService validation;
    private final SellerBankEncryptionService encryption;
    private final Clock clock;

    public SellerApplicationRevisionServiceImpl(
            com.msa.account.domain.notification.service.BusinessNoticeService notices,
            SellerAccessService access,
            AccountRepository accounts,
            SellerApplicationRepository apps,
            SellerProfileRepository profiles,
            SellerCorrectionRequestRepository corrections,
            SellerCertificateRepository certificates,
            CertificateStorage storage,
            CertificateValidationService validation,
            SellerBankEncryptionService encryption,
            Clock clock) {
        this.notices = notices;
        this.access = access;
        this.accounts = accounts;
        this.apps = apps;
        this.profiles = profiles;
        this.corrections = corrections;
        this.certificates = certificates;
        this.storage = storage;
        this.validation = validation;
        this.encryption = encryption;
        this.clock = clock;
    }

    public SellerCorrectionResponse requestCorrections(
            UUID admin, UUID id, RequestSellerCorrections request) {
        access.requireAdmin(admin, false);
        var owner = apps.findAccountIdById(id).orElseThrow(this::notFound);
        access.requireBuyer(owner, true);
        var a = apps.findLockedById(id).orElseThrow(this::notFound);
        if (!request.deadline().isAfter(clock.instant())
                || request.items().stream()
                                .map(RequestSellerCorrections.Item::field)
                                .distinct()
                                .count()
                        != request.items().size()) invalid();
        if (!apps.findFirstByAccount_IdOrderByAttemptNumberDesc(owner)
                .map(v -> v.getId().equals(id))
                .orElse(false)) invalid();
        a.requestChanges();
        var r =
                corrections.save(
                        SellerCorrectionRequest.builder()
                                .applicationId(id)
                                .accountId(owner)
                                .reviewerId(admin)
                                .request(request)
                                .now(clock.instant())
                                .build());
        apps.flush();
        notices.enqueue(
                owner,
                "SELLER_CORRECTION_REQUESTED",
                id,
                a.notificationVersion(),
                "SELLER_APPLICATION",
                id,
                r.getId(),
                r.getDeadline());
        return SellerCorrectionResponse.from(r, clock.instant());
    }

    @Transactional(readOnly = true)
    public SellerCorrectionResponse correction(UUID actor, UUID id, boolean admin) {
        if (admin) {
            access.requireAdmin(actor, false);
            apps.findById(id).orElseThrow(this::notFound);
        } else {
            access.requireBuyer(actor, false);
            apps.findByIdAndAccount_Id(id, actor).orElseThrow(this::notFound);
        }
        return SellerCorrectionResponse.from(
                corrections
                        .findFirstByApplicationIdOrderByCreatedAtDesc(id)
                        .orElseThrow(this::notFound),
                clock.instant());
    }

    @Transactional(noRollbackFor = SellerCorrectionExpiredException.class)
    public SellerApplicationCreatedResponse resubmit(
            UUID owner,
            UUID id,
            SellerCorrectionSubmissionRequest input,
            List<MultipartFile> files) {
        var account = access.requireBuyer(owner, true);
        var a = apps.findByIdAndAccount_Id(id, owner).orElseThrow(this::notFound);
        if (!apps.findFirstByAccount_IdOrderByAttemptNumberDesc(owner)
                .map(v -> v.getId().equals(id))
                .orElse(false)) invalid();
        var c =
                corrections
                        .findFirstByApplicationIdOrderByCreatedAtDesc(id)
                        .orElseThrow(this::notFound);
        if (c.getState() != SellerCorrectionRequest.State.OPEN
                || a.getStatus() != SellerApplicationStatus.CHANGES_REQUESTED) invalid();
        if (!clock.instant().isBefore(c.getDeadline())) {
            c.expire();
            a.expireCorrections();
            notifyExpiration(a, c);
            throw new SellerCorrectionExpiredException();
        }
        Set<SellerCorrectionField> allowed = new HashSet<>();
        c.getItems().forEach(i -> allowed.add(i.getField()));
        validatePatch(input, allowed);
        boolean hasFile = files != null && !files.isEmpty();
        if (hasFile && !allowed.contains(SellerCorrectionField.CERTIFICATE)) invalid();
        CertificateContent content;
        if (allowed.contains(SellerCorrectionField.CERTIFICATE))
            content = validation.validate(files);
        else {
            var original = certificates.findById(id).orElseThrow(this::notFound);
            content =
                    new CertificateContent(
                            original.getFileName(), storage.get(original.getObjectKey()));
        }
        var next =
                SellerApplication.corrected(
                        account,
                        a.getAttemptNumber() + 1,
                        a,
                        input,
                        input.bankAccount() == null
                                ? null
                                : encryption.encrypt(input.bankAccount().number()),
                        clock.instant());
        next.linkPrevious(a);
        apps.saveAndFlush(next);
        store(next, content);
        c.submitted(next.getId());
        return new SellerApplicationCreatedResponse(next.getId(), next.getStatus());
    }

    public SellerApplicationCreatedResponse recheck(UUID owner, List<MultipartFile> files) {
        var account = access.requireBuyer(owner, true);
        var profile =
                profiles.findById(owner)
                        .orElseThrow(() -> new SellerException(SellerErrorCode.SELLER_REQUIRED));
        var last =
                apps.findFirstByAccount_IdOrderByAttemptNumberDesc(owner)
                        .orElseThrow(this::notFound);
        if (last.getStatus() == SellerApplicationStatus.CHANGES_REQUESTED)
            corrections
                    .findFirstByApplicationIdOrderByCreatedAtDesc(last.getId())
                    .filter(
                            c ->
                                    c.getState() == SellerCorrectionRequest.State.OPEN
                                            && !clock.instant().isBefore(c.getDeadline()))
                    .ifPresent(
                            c -> {
                                c.expire();
                                last.expireCorrections();
                                notifyExpiration(last, c);
                            });
        if (last.getStatus() == SellerApplicationStatus.PENDING
                || last.getStatus() == SellerApplicationStatus.CHANGES_REQUESTED)
            throw new SellerException(SellerErrorCode.ALREADY_APPLIED);
        var content = validation.validate(files);
        var next =
                SellerApplication.recheck(
                        account, last.getAttemptNumber() + 1, profile, clock.instant());
        apps.saveAndFlush(next);
        store(next, content);
        // BUYER와 배송·문의용 역할은 유지하고 신규 상품 업무의 최신 자격 조회만 제한합니다.
        profile.restrictSales(true, clock.instant());
        return new SellerApplicationCreatedResponse(next.getId(), next.getStatus());
    }

    @Transactional(readOnly = true)
    public SellerReviewStateResponse state(UUID owner) {
        access.requireBuyer(owner, false);
        var p =
                profiles.findById(owner)
                        .orElseThrow(() -> new SellerException(SellerErrorCode.SELLER_REQUIRED));
        var a =
                apps.findFirstByAccount_IdOrderByAttemptNumberDesc(owner)
                        .orElseThrow(this::notFound);
        var c =
                corrections
                        .findFirstByApplicationIdOrderByCreatedAtDesc(a.getId())
                        .map(r -> SellerCorrectionResponse.from(r, clock.instant()))
                        .orElse(null);
        return SellerReviewStateResponse.builder()
                .salesRestricted(p.isSalesRestricted())
                .application(SellerApplicationResponse.from(a))
                .correction(c)
                .build();
    }

    public void expire(UUID id) {
        var owner = corrections.findAccountId(id).orElse(null);
        if (owner == null) return;
        accounts.findLockedById(owner).orElseThrow(this::notFound);
        var c = corrections.findById(id).orElseThrow(this::notFound);
        var a = apps.findLockedById(c.getApplicationId()).orElseThrow(this::notFound);
        if (c.getState() == SellerCorrectionRequest.State.OPEN
                && !clock.instant().isBefore(c.getDeadline())) {
            c.expire();
            a.expireCorrections();
            notifyExpiration(a, c);
        }
    }

    /** 만료가 실제 커밋되는 모든 경로에서 같은 버전과 ID의 알림을 보관합니다. */
    private void notifyExpiration(SellerApplication a, SellerCorrectionRequest c) {
        apps.flush();
        notices.enqueue(
                c.getAccountId(),
                "SELLER_CORRECTION_EXPIRED",
                a.getId(),
                a.notificationVersion(),
                "SELLER_APPLICATION",
                a.getId(),
                c.getId(),
                c.getDeadline());
    }

    private void store(SellerApplication a, CertificateContent content) {
        String key = "seller-certificates/" + a.getId() + "/" + UUID.randomUUID() + ".pdf";
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK)
                            try {
                                storage.delete(key);
                            } catch (RuntimeException ex) {
                                log.warn(
                                        "Revision certificate cleanup requires retry: type={}",
                                        ex.getClass().getSimpleName());
                            }
                    }
                });
        storage.put(key, content);
        certificates.save(
                SellerCertificate.builder()
                        .applicationId(a.getId())
                        .key(key)
                        .length(content.bytes().length)
                        .build());
    }

    private void validatePatch(
            SellerCorrectionSubmissionRequest r, Set<SellerCorrectionField> fields) {
        if (fields.contains(SellerCorrectionField.STORE_NAME) != (r.sellerDisplayName() != null)
                || r.sellerDisplayName() != null && r.sellerDisplayName().isBlank()) invalid();
        if (fields.contains(SellerCorrectionField.CONTACT) != (r.contact() != null)) invalid();
        if (fields.contains(SellerCorrectionField.BANK_ACCOUNT) != (r.bankAccount() != null))
            invalid();
        if (fields.contains(SellerCorrectionField.DISPATCH_ADDRESS)
                != (r.dispatchAddress() != null)) invalid();
        if (fields.contains(SellerCorrectionField.RETURN_ADDRESS) != (r.returnAddress() != null))
            invalid();
    }

    private void invalid() {
        throw new SellerException(SellerErrorCode.INVALID_REVIEW_REQUEST);
    }

    private SellerException notFound() {
        return new SellerException(SellerErrorCode.APPLICATION_NOT_FOUND);
    }
}
