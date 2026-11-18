package com.msa.account.domain.seller.query.dto.response;

import lombok.Builder;

import java.time.Instant;

/** 본인 스토어 설정 응답입니다. 로고 객체 키와 개인정보는 공개하지 않습니다. */
@Builder
public record StoreResponse(
        String sellerDisplayName, String introduction, boolean hasLogo, Instant updatedAt) {
    @Override
    public String toString() {
        return "StoreResponse[REDACTED]";
    }

    public static class StoreResponseBuilder {
        @Override
        public String toString() {
            return "StoreResponseBuilder[REDACTED]";
        }
    }
}
