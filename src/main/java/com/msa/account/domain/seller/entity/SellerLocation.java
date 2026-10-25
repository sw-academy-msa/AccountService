package com.msa.account.domain.seller.entity;
import com.msa.account.domain.seller.command.dto.request.SellerAddressRequest;
import jakarta.persistence.*;
/** 신청 당시 주소 값입니다. 이후 구매자 주소록 변경에 영향받지 않습니다. */
@Embeddable
public class SellerLocation {
    private String postalCode;
    private String roadAddress;
    private String detailAddress;
    protected SellerLocation() { }
    public SellerLocation(SellerAddressRequest input) {
        postalCode=input.postalCode(); roadAddress=input.roadAddress(); detailAddress=input.detailAddress();
    }
    // 승인 신청서와 프로필이 동일한 Embeddable 인스턴스를 공유하지 않도록 값으로 복사합니다.
    public SellerLocation(SellerLocation source) {
        postalCode=source.postalCode; roadAddress=source.roadAddress; detailAddress=source.detailAddress;
    }
    public String getPostalCode() { return postalCode; }
    public String getRoadAddress() { return roadAddress; }
    public String getDetailAddress() { return detailAddress; }
}
