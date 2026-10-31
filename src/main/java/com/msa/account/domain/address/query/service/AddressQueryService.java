package com.msa.account.domain.address.query.service;
import com.msa.account.domain.address.query.dto.response.AddressResponse;
import java.util.*;
/** 본인 배송지 목록·단건 조회 계약입니다. */
public interface AddressQueryService {
    List<AddressResponse> list(UUID accountId);
    AddressResponse get(UUID accountId, UUID addressId);
}
