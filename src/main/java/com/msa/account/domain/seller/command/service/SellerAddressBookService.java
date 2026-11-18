package com.msa.account.domain.seller.command.service;

import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.enums.SellerAddressType;
import com.msa.account.domain.seller.query.dto.response.SellerAddressEntryResponse;

import java.util.*;

/** 복수 출고지와 반품지의 변경 계약이며 기본 주소는 기존 프로필 API에도 반영합니다. */
public interface SellerAddressBookService {
    List<SellerAddressEntryResponse> list(UUID owner, SellerAddressType type);

    SellerAddressEntryResponse get(UUID owner, SellerAddressType type, UUID id);

    SellerAddressEntryResponse create(
            UUID owner, SellerAddressType type, SellerAddressEntryRequest request);

    SellerAddressEntryResponse update(
            UUID owner, SellerAddressType type, UUID id, SellerAddressEntryRequest request);

    void selectDefault(UUID owner, SellerAddressType type, UUID id);

    void delete(UUID owner, SellerAddressType type, UUID id);

    void synchronizeLegacy(UUID owner);
}
