package com.msa.account.domain.address.command.dto.request;

import jakarta.validation.constraints.*;

/** 등록·수정에 공통으로 쓰는 배송지 입력입니다. 검색 결과의 우편번호·도로명주소와 직접 입력을 분리합니다. */
public record AddressRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Pattern(regexp = "^0[0-9]{1,2}-?[0-9]{3,4}-?[0-9]{4}$",
                message = "수령인 연락처 형식을 확인해 주세요.") String recipientPhone,
        @NotBlank @Pattern(regexp = "[0-9]{5}", message = "우편번호는 숫자 5자리여야 합니다.") String postalCode,
        @NotBlank @Size(max = 200) String roadAddress,
        @Size(max = 200) String detailAddress,
        @Size(max = 500) String deliveryMemo) {
    public AddressRequest {
        label = trim(label); recipientName = trim(recipientName);
        recipientPhone = trim(recipientPhone); postalCode = trim(postalCode);
        roadAddress = trim(roadAddress); detailAddress = trim(detailAddress); deliveryMemo = trim(deliveryMemo);
    }
    // 개인정보를 자동 생성된 record 문자열로 로그에 노출하지 않습니다.
    @Override public String toString() { return "AddressRequest[REDACTED]"; }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
