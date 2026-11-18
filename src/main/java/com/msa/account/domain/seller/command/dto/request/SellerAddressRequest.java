package com.msa.account.domain.seller.command.dto.request;
import jakarta.validation.constraints.*;
/** 구매자 주소록과 별개로 신청 당시 출고·반품 주소를 보존합니다. */
public record SellerAddressRequest(
        @NotBlank @Pattern(regexp="[0-9]{5}") String postalCode,
        @NotBlank @Size(max=200) String roadAddress,
        @Size(max=200) String detailAddress) {
    public SellerAddressRequest {
        if(postalCode!=null) postalCode=postalCode.trim();
        if(roadAddress!=null) roadAddress=roadAddress.trim();
        if(detailAddress!=null) detailAddress=detailAddress.trim();
    }
    @Override public String toString() { return "SellerAddressRequest[REDACTED]"; }
}
