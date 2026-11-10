package com.msa.account.domain.seller.query.dto.response;

import lombok.Builder;

/** 판매 제한 중에도 BUYER 권한으로 본인의 재심사 상태와 보완 사항을 볼 수 있습니다. */
@Builder
public record SellerReviewStateResponse(
        boolean salesRestricted,
        SellerApplicationResponse application,
        SellerCorrectionResponse correction) {
    @Override
    public String toString() {
        return "SellerReviewStateResponse[REDACTED]";
    }

    public static class SellerReviewStateResponseBuilder {
        @Override
        public String toString() {
            return "SellerReviewStateResponseBuilder[REDACTED]";
        }
    }
}
