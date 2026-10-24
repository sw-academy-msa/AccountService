package com.msa.account.domain.address.query.service.impl;

import com.msa.account.domain.address.query.service.AddressQueryService;
import com.msa.account.domain.address.query.dto.response.AddressResponse;
import com.msa.account.domain.address.repository.AddressRepository;
import com.msa.account.domain.address.service.AddressAccessService;
import com.msa.account.domain.address.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 기본 배송지를 먼저 반환하며 모든 조회를 현재 구매자와 소유자 범위로 제한합니다. */
@Service
@Transactional(readOnly = true)
public class AddressQueryServiceImpl implements AddressQueryService {
    private final AddressAccessService access;
    private final AddressRepository addresses;
    public AddressQueryServiceImpl(AddressAccessService access, AddressRepository addresses) {
        this.access = access; this.addresses = addresses;
    }
    @Override
    public List<AddressResponse> list(UUID accountId) {
        access.requireBuyer(accountId);
        return addresses.findAllByAccountIdOrderByDefaultAddressDescCreatedAtAscIdAsc(accountId)
                .stream().map(AddressResponse::from).toList();
    }
    @Override
    public AddressResponse get(UUID accountId, UUID addressId) {
        access.requireBuyer(accountId);
        return AddressResponse.from(addresses.findByIdAndAccountId(addressId, accountId)
                .orElseThrow(() -> new AddressException(AddressErrorCode.ADDRESS_NOT_FOUND)));
    }
}
