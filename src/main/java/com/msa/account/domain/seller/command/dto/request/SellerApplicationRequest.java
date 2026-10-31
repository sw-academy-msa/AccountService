package com.msa.account.domain.seller.command.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
/** 피그마 신청 항목입니다. 사업자명·등록번호·쇼핑몰 URL은 별도로 수집하지 않습니다. */
public record SellerApplicationRequest(
        @Size(max=100) String sellerDisplayName,
        @NotBlank @Size(max=100) String contactName,
        @NotBlank @Email @Size(max=320) String contactEmail,
        @NotBlank @Pattern(regexp="^0[0-9]{1,2}-?[0-9]{3,4}-?[0-9]{4}$") String contactPhone,
        @NotBlank @Size(max=50) String bankName,
        @NotBlank @Pattern(regexp="[0-9]{6,30}") String bankAccountNumber,
        @NotNull @Valid SellerAddressRequest dispatchAddress,
        @NotNull @Valid SellerAddressRequest returnAddress) {
    public SellerApplicationRequest {
        sellerDisplayName=trim(sellerDisplayName); contactName=trim(contactName);
        contactEmail=trim(contactEmail); if(contactEmail!=null) contactEmail=contactEmail.toLowerCase(Locale.ROOT);
        contactPhone=trim(contactPhone); bankName=trim(bankName); bankAccountNumber=trim(bankAccountNumber);
        if(bankAccountNumber!=null) bankAccountNumber=bankAccountNumber.replace("-","");
    }
    private static String trim(String value) { return value==null?null:value.trim(); }
    @Override public String toString() { return "SellerApplicationRequest[REDACTED]"; }
}
