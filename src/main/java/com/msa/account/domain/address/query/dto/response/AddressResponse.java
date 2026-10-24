package com.msa.account.domain.address.query.dto.response;

import com.msa.account.domain.address.entity.Address;
import java.time.Instant;
import java.util.UUID;

/** 배송지 조회용 HTTP 응답입니다. 계정 내부 정보와 JPA 객체는 노출하지 않습니다. */
public record AddressResponse(UUID addressId, String label, String recipientName, String recipientPhone,
        String postalCode, String roadAddress, String detailAddress, String deliveryMemo,
        boolean defaultAddress, Instant createdAt, Instant updatedAt) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                address.getRecipientPhone(), address.getPostalCode(), address.getRoadAddress(),
                address.getDetailAddress(), address.getDeliveryMemo(), address.isDefaultAddress(),
                address.getCreatedAt(), address.getUpdatedAt());
    }
    @Override public String toString() { return "AddressResponse[REDACTED]"; }
}
