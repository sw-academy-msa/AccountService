package com.msa.account.domain.address.command.dto.response;
import java.util.UUID;
/** 등록 직후 생성된 식별자와 서버가 결정한 기본 여부를 반환합니다. */
public record AddressCreatedResponse(UUID addressId, boolean defaultAddress) { }
