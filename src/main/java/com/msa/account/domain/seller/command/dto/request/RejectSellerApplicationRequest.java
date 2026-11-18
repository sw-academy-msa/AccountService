package com.msa.account.domain.seller.command.dto.request;
import jakarta.validation.constraints.*;
/** 거절 사유는 필수이며 신청자가 결과를 조회할 때 함께 표시합니다. */
public record RejectSellerApplicationRequest(@NotBlank @Size(max=1000) String reason) {
    public RejectSellerApplicationRequest { if(reason!=null) reason=reason.trim(); }
}
