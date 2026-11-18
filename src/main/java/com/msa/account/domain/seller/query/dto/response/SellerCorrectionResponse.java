package com.msa.account.domain.seller.query.dto.response;

import com.msa.account.domain.seller.entity.SellerCorrectionRequest;
import com.msa.account.domain.seller.enums.SellerCorrectionField;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** HTTP 전용 보완 요청 결과입니다. 현재 시각의 기한 경과를 스케줄러 지연과 무관하게 표시합니다. */
@Builder
public record SellerCorrectionResponse(
        UUID id,
        UUID applicationId,
        Instant deadline,
        String state,
        List<Item> items,
        UUID submittedApplicationId) {
    @Builder
    public record Item(SellerCorrectionField field, String reason) {
        @Override
        public String toString() {
            return "CorrectionItem[REDACTED]";
        }
    }

    public SellerCorrectionResponse {
        items = List.copyOf(items);
    }

    public static SellerCorrectionResponse from(SellerCorrectionRequest r, Instant now) {
        return SellerCorrectionResponse.builder()
                .id(r.getId())
                .applicationId(r.getApplicationId())
                .deadline(r.getDeadline())
                .state(
                        r.getState() == SellerCorrectionRequest.State.OPEN
                                        && !now.isBefore(r.getDeadline())
                                ? "EXPIRED"
                                : r.getState().name())
                .items(
                        r.getItems().stream()
                                .map(
                                        i ->
                                                Item.builder()
                                                        .field(i.getField())
                                                        .reason(i.getReason())
                                                        .build())
                                .toList())
                .submittedApplicationId(r.getSubmittedApplicationId())
                .build();
    }

    @Override
    public String toString() {
        return "SellerCorrectionResponse[REDACTED]";
    }

    public static class SellerCorrectionResponseBuilder {
        @Override
        public String toString() {
            return "SellerCorrectionResponseBuilder[REDACTED]";
        }
    }
}
