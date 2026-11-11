package com.msa.account.domain.seller.command.service.impl;

import com.msa.account.domain.seller.command.dto.request.UpdateStoreRequest;
import com.msa.account.domain.seller.command.service.SellerStoreService;
import com.msa.account.domain.seller.entity.SellerProfile;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.domain.seller.repository.SellerProfileRepository;
import com.msa.account.domain.seller.service.SellerAccessService;
import com.msa.account.domain.seller.storage.LogoStorage;

import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.Clock;
import java.util.*;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** 본인 판매자 계정을 먼저 잠그고 소개와 로고만 변경합니다. S3는 DB와 별도 자원이므로 보상 삭제합니다. */
@Service
@Transactional
public class SellerStoreServiceImpl implements SellerStoreService {
    private static final Logger log = LoggerFactory.getLogger(SellerStoreServiceImpl.class);
    private final SellerAccessService access;
    private final SellerProfileRepository profiles;
    private final com.msa.account.domain.seller.repository.SellerApplicationRepository applications;
    private final LogoStorage storage;
    private final Clock clock;

    public SellerStoreServiceImpl(
            SellerAccessService access,
            SellerProfileRepository profiles,
            LogoStorage storage,
            Clock clock,
            com.msa.account.domain.seller.repository.SellerApplicationRepository applications) {
        this.access = access;
        this.profiles = profiles;
        this.storage = storage;
        this.clock = clock;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID seller) {
        access.requireSeller(seller, false);
        return response(profile(seller));
    }

    public StoreResponse update(UUID seller, UpdateStoreRequest r) {
        access.requireSeller(seller, true);
        var p = profile(seller);
        p.updateIntroduction(r.introduction(), clock.instant());
        return response(p);
    }

    public StoreResponse upload(UUID seller, MultipartFile image) {
        access.requireSeller(seller, true);
        var p = profile(seller);
        if (image == null || image.isEmpty() || image.getSize() > 2 * 1024 * 1024)
            throw new SellerException(SellerErrorCode.INVALID_LOGO);
        byte[] bytes;
        String type;
        try {
            bytes = image.getBytes();
            if (bytes.length > 2 * 1024 * 1024)
                throw new SellerException(SellerErrorCode.INVALID_LOGO);
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new SellerException(SellerErrorCode.INVALID_LOGO);
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    type =
                            switch (format) {
                                case "png" -> "image/png";
                                case "jpeg", "jpg" -> "image/jpeg";
                                default -> throw new SellerException(SellerErrorCode.INVALID_LOGO);
                            };
                    if (reader.getWidth(0) > 4096
                            || reader.getHeight(0) > 4096
                            || (long) reader.getWidth(0) * reader.getHeight(0) > 16_777_216)
                        throw new SellerException(SellerErrorCode.INVALID_LOGO);
                    // 확장자와 Content-Type 대신 실제 디코딩으로 위장 파일과 깨진 이미지를 거절합니다.
                    if (reader.read(0) == null)
                        throw new SellerException(SellerErrorCode.INVALID_LOGO);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw new SellerException(SellerErrorCode.INVALID_LOGO);
        }
        String next = "seller-logos/" + seller + "/" + UUID.randomUUID();
        String previous = p.getLogoObjectKey();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) cleanup(next);
                    }

                    public void afterCommit() {
                        if (previous != null) cleanup(previous);
                    }
                });
        storage.put(next, type, bytes);
        p.changeLogo(next, type, clock.instant());
        return response(p);
    }

    @Transactional(readOnly = true)
    public LogoContentResponse logo(UUID seller) {
        access.requireSeller(seller, false);
        var p = profile(seller);
        if (p.getLogoObjectKey() == null) throw new SellerException(SellerErrorCode.LOGO_NOT_FOUND);
        return LogoContentResponse.builder()
                .contentType(p.getLogoContentType())
                .bytes(storage.get(p.getLogoObjectKey()))
                .build();
    }

    public void deleteLogo(UUID seller) {
        access.requireSeller(seller, true);
        var p = profile(seller);
        String previous = p.getLogoObjectKey();
        p.changeLogo(null, null, clock.instant());
        if (previous != null)
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        public void afterCommit() {
                            cleanup(previous);
                        }
                    });
    }

    private SellerProfile profile(UUID id) {
        return profiles.findById(id)
                .orElseGet(
                        () -> {
                            boolean readOnly =
                                    TransactionSynchronizationManager
                                            .isCurrentTransactionReadOnly();
                            var owner = access.requireSeller(id, !readOnly);
                            var approved =
                                    applications
                                            .findFirstByAccount_IdAndStatusOrderByAttemptNumberDesc(
                                                    id,
                                                    com.msa.account.domain.seller.enums
                                                            .SellerApplicationStatus.APPROVED)
                                            .orElseThrow(
                                                    () ->
                                                            new SellerException(
                                                                    SellerErrorCode
                                                                            .SELLER_REQUIRED));
                            var p =
                                    SellerProfile.builder()
                                            .owner(owner)
                                            .approved(approved)
                                            .now(clock.instant())
                                            .build();
                            // 이전 개발 데이터도 조회에는 쓰기 없이 호환하고 첫 변경에서만 현재 프로필을 생성합니다.
                            return readOnly ? p : profiles.save(p);
                        });
    }

    private StoreResponse response(SellerProfile p) {
        return StoreResponse.builder()
                .sellerDisplayName(p.getSellerDisplayName())
                .introduction(p.getStoreIntroduction())
                .hasLogo(p.getLogoObjectKey() != null)
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private void cleanup(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Seller logo cleanup requires retry: type={}", ex.getClass().getSimpleName());
        }
    }
}
