package com.msa.account.domain.support.dto.response;

import com.msa.account.domain.support.entity.SupportInquiry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 1:1 문의 응답입니다. */
public final class SupportResponses {
    private SupportResponses() {}

    /** accountId는 운영자 목록에서만 채웁니다(본인 조회에는 넣지 않습니다). */
    public record Inquiry(
            UUID id,
            UUID accountId,
            SupportInquiry.Category category,
            String title,
            String body,
            UUID orderId,
            SupportInquiry.Status status,
            String answer,
            Instant answeredAt,
            Instant createdAt) {
        public static Inquiry of(SupportInquiry s, boolean admin) {
            return new Inquiry(
                    s.getId(),
                    admin ? s.getAccountId() : null,
                    s.getCategory(),
                    s.getTitle(),
                    s.getBody(),
                    s.getOrderId(),
                    s.getStatus(),
                    s.getAnswer(),
                    s.getAnsweredAt(),
                    s.getCreatedAt());
        }
    }

    public record Page(List<Inquiry> content, int page, int size, long totalElements, int totalPages) {
        public Page {
            content = List.copyOf(content);
        }

        public static Page of(org.springframework.data.domain.Page<SupportInquiry> p, boolean admin) {
            return new Page(
                    p.getContent().stream().map(s -> Inquiry.of(s, admin)).toList(),
                    p.getNumber(),
                    p.getSize(),
                    p.getTotalElements(),
                    p.getTotalPages());
        }
    }
}
