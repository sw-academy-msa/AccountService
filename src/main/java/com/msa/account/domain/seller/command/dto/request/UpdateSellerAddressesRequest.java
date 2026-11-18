package com.msa.account.domain.seller.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 판매자의 현재 출고·반품 주소입니다. 동일 주소 선택 시 프론트가 같은 값을 전달합니다. */
public record UpdateSellerAddressesRequest(
        @NotNull @Valid SellerAddressRequest dispatchAddress,
        @NotNull @Valid SellerAddressRequest returnAddress) {
    @Override public String toString() { return "UpdateSellerAddressesRequest[REDACTED]"; }
}
