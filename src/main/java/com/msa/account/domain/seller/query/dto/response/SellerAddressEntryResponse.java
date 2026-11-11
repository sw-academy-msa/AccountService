package com.msa.account.domain.seller.query.dto.response;

import com.msa.account.domain.seller.entity.SellerAddressEntry;
import com.msa.account.domain.seller.enums.SellerAddressType;

import lombok.Builder;

import java.util.UUID;

/** 주소 엔티티를 반환하지 않고 본인 화면에 필요한 값만 복사합니다. */
@Builder
public record SellerAddressEntryResponse(
        UUID id,
        SellerAddressType type,
        String label,
        String contactName,
        String contactPhone,
        SellerApplicationResponse.Location address,
        boolean defaultAddress) {
    public static SellerAddressEntryResponse from(SellerAddressEntry e, UUID selected) {
        return SellerAddressEntryResponse.builder()
                .id(e.getId())
                .type(e.getType())
                .label(e.getLabel())
                .contactName(e.getContactName())
                .contactPhone(e.getContactPhone())
                .address(SellerApplicationResponse.Location.from(e.getAddress()))
                .defaultAddress(e.getId().equals(selected))
                .build();
    }

    @Override
    public String toString() {
        return "SellerAddressEntryResponse[REDACTED]";
    }

    public static class SellerAddressEntryResponseBuilder {
        @Override
        public String toString() {
            return "SellerAddressEntryResponseBuilder[REDACTED]";
        }
    }
}
