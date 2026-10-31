package com.msa.account.domain.address.command.service;

import com.msa.account.domain.address.command.dto.request.AddressRequest;
import com.msa.account.domain.address.command.dto.response.AddressCreatedResponse;
import java.util.UUID;

/** 구매자의 배송지 등록·수정·삭제·기본 지정 계약입니다. */
public interface AddressCommandService {
    AddressCreatedResponse create(UUID accountId, AddressRequest request);
    void update(UUID accountId, UUID addressId, AddressRequest request);
    void delete(UUID accountId, UUID addressId);
    void setDefault(UUID accountId, UUID addressId);
}
