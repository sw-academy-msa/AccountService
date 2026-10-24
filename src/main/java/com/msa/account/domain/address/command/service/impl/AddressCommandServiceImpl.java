package com.msa.account.domain.address.command.service.impl;

import com.msa.account.domain.address.command.dto.request.AddressRequest;
import com.msa.account.domain.address.command.dto.response.AddressCreatedResponse;
import com.msa.account.domain.address.command.service.AddressCommandService;
import com.msa.account.domain.address.entity.Address;
import com.msa.account.domain.address.exception.*;
import com.msa.account.domain.address.repository.AddressRepository;
import com.msa.account.domain.address.service.AddressAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** 같은 회원의 모든 배송지 쓰기를 직렬화해 기본 배송지가 여러 개 생기는 것을 방지합니다. */
@Service
@Transactional
public class AddressCommandServiceImpl implements AddressCommandService {
    private final AddressAccessService access;
    private final AddressRepository addresses;
    private final Clock clock;
    public AddressCommandServiceImpl(AddressAccessService access, AddressRepository addresses, Clock clock) {
        this.access = access; this.addresses = addresses; this.clock = clock;
    }
    @Override
    public AddressCreatedResponse create(UUID accountId, AddressRequest request) {
        var owner = access.requireBuyerForUpdate(accountId);
        boolean first = addresses.countByAccountId(accountId) == 0;
        var address = addresses.save(new Address(owner, request, first, clock.instant()));
        return new AddressCreatedResponse(address.getId(), address.isDefaultAddress());
    }
    @Override
    public void update(UUID accountId, UUID addressId, AddressRequest request) {
        access.requireBuyerForUpdate(accountId);
        owned(accountId, addressId).update(request, clock.instant());
    }
    @Override
    public void delete(UUID accountId, UUID addressId) {
        access.requireBuyerForUpdate(accountId);
        var address = owned(accountId, addressId);
        // 유일한 배송지는 삭제할 수 있지만 다른 배송지가 남으면 기본 주소부터 변경해야 합니다.
        if (address.isDefaultAddress() && addresses.countByAccountId(accountId) > 1)
            throw new AddressException(AddressErrorCode.DEFAULT_CHANGE_REQUIRED);
        addresses.delete(address);
    }
    @Override
    public void setDefault(UUID accountId, UUID addressId) {
        access.requireBuyerForUpdate(accountId);
        var selected = owned(accountId, addressId);
        var now = clock.instant();
        // 선택 대상의 소유권을 먼저 확인하여 잘못된 요청이 기존 기본 주소를 해제하지 않게 합니다.
        var all = addresses.findAllByAccountIdOrderByDefaultAddressDescCreatedAtAscIdAsc(accountId);
        all.stream().filter(address -> !address.getId().equals(addressId))
                .forEach(address -> address.setDefault(false, now));
        addresses.flush();
        selected.setDefault(true, now);
    }
    private Address owned(UUID accountId, UUID addressId) {
        return addresses.findByIdAndAccountId(addressId, accountId)
                .orElseThrow(() -> new AddressException(AddressErrorCode.ADDRESS_NOT_FOUND));
    }
}
