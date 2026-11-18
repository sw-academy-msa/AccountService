package com.msa.account.domain.seller.command.service.impl;

import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.command.service.SellerAddressBookService;
import com.msa.account.domain.seller.entity.*;
import com.msa.account.domain.seller.enums.*;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

/** 모든 쓰기는 계정 행부터 잠급니다. 조회는 기존 주소를 가상 DTO로 제공하고 데이터를 생성하지 않습니다. */
@Service
@Transactional
public class SellerAddressBookServiceImpl implements SellerAddressBookService {
    private final SellerAccessService access;
    private final SellerProfileRepository profiles;
    private final SellerApplicationRepository applications;
    private final SellerAddressEntryRepository entries;
    private final Clock clock;

    public SellerAddressBookServiceImpl(
            SellerAccessService access,
            SellerProfileRepository profiles,
            SellerApplicationRepository applications,
            SellerAddressEntryRepository entries,
            Clock clock) {
        this.access = access;
        this.profiles = profiles;
        this.applications = applications;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SellerAddressEntryResponse> list(UUID owner, SellerAddressType type) {
        access.requireSeller(owner, false);
        var p = profile(owner, false);
        var rows = entries.findOwned(owner, type);
        if (rows.isEmpty())
            return List.of(SellerAddressEntryResponse.from(legacy(p, type), legacyId(owner, type)));
        return rows.stream()
                .map(e -> SellerAddressEntryResponse.from(e, selected(p, type)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerAddressEntryResponse get(UUID owner, SellerAddressType type, UUID id) {
        return list(owner, type).stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(this::notFound);
    }

    public SellerAddressEntryResponse create(
            UUID owner, SellerAddressType type, SellerAddressEntryRequest r) {
        access.requireSeller(owner, true);
        var p = profile(owner, true);
        initialize(p, type);
        var e =
                entries.save(
                        SellerAddressEntry.builder()
                                .id(UUID.randomUUID())
                                .accountId(owner)
                                .type(type)
                                .request(r)
                                .now(clock.instant())
                                .build());
        return SellerAddressEntryResponse.from(e, selected(p, type));
    }

    public SellerAddressEntryResponse update(
            UUID owner, SellerAddressType type, UUID id, SellerAddressEntryRequest r) {
        access.requireSeller(owner, true);
        var p = profile(owner, true);
        initialize(p, type);
        var e = owned(owner, type, id);
        e.update(r, clock.instant());
        if (id.equals(selected(p, type)))
            p.selectAddress(type, id, e.getAddress(), clock.instant());
        return SellerAddressEntryResponse.from(e, selected(p, type));
    }

    public void selectDefault(UUID owner, SellerAddressType type, UUID id) {
        access.requireSeller(owner, true);
        var p = profile(owner, true);
        initialize(p, type);
        var e = owned(owner, type, id);
        p.selectAddress(type, id, e.getAddress(), clock.instant());
    }

    public void delete(UUID owner, SellerAddressType type, UUID id) {
        access.requireSeller(owner, true);
        var p = profile(owner, true);
        initialize(p, type);
        var e = owned(owner, type, id);
        // 승인 판매자 프로필은 출고지와 반품지가 필수이므로 기본 주소는 다른 주소를 지정한 뒤 삭제합니다.
        if (id.equals(selected(p, type)))
            throw new SellerException(SellerErrorCode.DEFAULT_ADDRESS_REQUIRED);
        entries.delete(e);
    }

    public void synchronizeLegacy(UUID owner) {
        access.requireSeller(owner, true);
        var p = profile(owner, true);
        for (var type : SellerAddressType.values()) {
            var location =
                    type == SellerAddressType.DISPATCH
                            ? p.getDispatchAddress()
                            : p.getReturnAddress();
            if (entries.findOwned(owner, type).isEmpty()) initialize(p, type);
            else {
                var e = owned(owner, type, selected(p, type));
                e.update(
                        new SellerAddressEntryRequest(
                                e.getLabel(),
                                e.getContactName(),
                                e.getContactPhone(),
                                new SellerAddressRequest(
                                        location.getPostalCode(),
                                        location.getRoadAddress(),
                                        location.getDetailAddress())),
                        clock.instant());
            }
        }
    }

    private SellerProfile profile(UUID owner, boolean persist) {
        return profiles.findById(owner)
                .orElseGet(
                        () -> {
                            var application =
                                    applications
                                            .findFirstByAccount_IdAndStatusOrderByAttemptNumberDesc(
                                                    owner, SellerApplicationStatus.APPROVED)
                                            .orElseThrow(
                                                    () ->
                                                            new SellerException(
                                                                    SellerErrorCode
                                                                            .SELLER_REQUIRED));
                            var account = access.requireSeller(owner, persist);
                            var p =
                                    SellerProfile.builder()
                                            .owner(account)
                                            .approved(application)
                                            .now(clock.instant())
                                            .build();
                            return persist ? profiles.save(p) : p;
                        });
    }

    private void initialize(SellerProfile p, SellerAddressType type) {
        if (entries.findOwned(p.getAccountId(), type).isEmpty()) {
            var e = entries.save(legacy(p, type));
            p.selectAddress(type, e.getId(), e.getAddress(), clock.instant());
        }
    }

    private SellerAddressEntry legacy(SellerProfile p, SellerAddressType type) {
        var a = type == SellerAddressType.DISPATCH ? p.getDispatchAddress() : p.getReturnAddress();
        return SellerAddressEntry.builder()
                .id(legacyId(p.getAccountId(), type))
                .accountId(p.getAccountId())
                .type(type)
                .request(
                        new SellerAddressEntryRequest(
                                type == SellerAddressType.DISPATCH ? "기본 출고지" : "기본 반품지",
                                p.getContactName(),
                                p.getContactPhone(),
                                new SellerAddressRequest(
                                        a.getPostalCode(),
                                        a.getRoadAddress(),
                                        a.getDetailAddress())))
                .now(clock.instant())
                .build();
    }

    private UUID legacyId(UUID owner, SellerAddressType type) {
        return UUID.nameUUIDFromBytes(
                ("seller-address:" + owner + ":" + type).getBytes(StandardCharsets.UTF_8));
    }

    private UUID selected(SellerProfile p, SellerAddressType type) {
        var id =
                type == SellerAddressType.DISPATCH
                        ? p.getDefaultDispatchEntryId()
                        : p.getDefaultReturnEntryId();
        return id == null ? legacyId(p.getAccountId(), type) : id;
    }

    private SellerAddressEntry owned(UUID owner, SellerAddressType type, UUID id) {
        return entries.findOwned(owner, type, id).orElseThrow(this::notFound);
    }

    private SellerException notFound() {
        return new SellerException(SellerErrorCode.ADDRESS_NOT_FOUND);
    }
}
