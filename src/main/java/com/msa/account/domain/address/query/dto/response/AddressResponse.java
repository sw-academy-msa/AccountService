package com.msa.account.domain.address.query.dto.response;

import lombok.Builder;

import com.msa.account.domain.address.entity.Address;
import java.time.Instant;
import java.util.UUID;

/** 배송지 조회용 HTTP 응답입니다. 계정 내부 정보와 JPA 객체는 노출하지 않습니다. */
// 응답 필드 이름을 명시해 같은 타입의 인자 순서가 뒤바뀌는 실수를 줄입니다.
@Builder
public record AddressResponse(UUID addressId, String label, String recipientName, String recipientPhone,
        String postalCode, String roadAddress, String detailAddress, String deliveryMemo,
        boolean defaultAddress, Instant createdAt, Instant updatedAt) {
    public static AddressResponse from(Address address) {
        return AddressResponse.builder()
                .addressId(address.getId())
                .label(address.getLabel())
                .recipientName(address.getRecipientName())
                .recipientPhone(address.getRecipientPhone())
                .postalCode(address.getPostalCode())
                .roadAddress(address.getRoadAddress())
                .detailAddress(address.getDetailAddress())
                .deliveryMemo(address.getDeliveryMemo())
                .defaultAddress(address.isDefaultAddress())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
    @Override public String toString() { return "AddressResponse[REDACTED]"; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class AddressResponseBuilder {
        @Override
        public String toString() {
            return "AddressResponseBuilder[REDACTED]";
        }
    }
}
