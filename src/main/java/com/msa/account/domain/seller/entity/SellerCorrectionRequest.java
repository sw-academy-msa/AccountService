package com.msa.account.domain.seller.entity;

import com.msa.account.domain.seller.command.dto.request.RequestSellerCorrections;
import com.msa.account.domain.seller.enums.SellerCorrectionField;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.*;

/** 신청 원본과 보완 항목을 별도 이력으로 보존합니다. 새 제출은 새 신청 회차를 가리킵니다. */
@Entity
@Getter
@Table(
        schema = "account",
        name = "seller_correction_requests",
        indexes = @Index(name = "ix_seller_correction_deadline", columnList = "state,deadline"))
public class SellerCorrectionRequest {
    public enum State {
        OPEN,
        SUBMITTED,
        EXPIRED
    }

    @Id private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false)
    private UUID reviewerId;

    @Column(nullable = false, updatable = false)
    private Instant deadline;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    private UUID submittedApplicationId;

    @ElementCollection
    @CollectionTable(
            schema = "account",
            name = "seller_correction_items",
            joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "position")
    private List<Item> items = new ArrayList<>();

    @Version private Long version;

    protected SellerCorrectionRequest() {}

    @Builder
    public SellerCorrectionRequest(
            UUID applicationId,
            UUID accountId,
            UUID reviewerId,
            RequestSellerCorrections request,
            Instant now) {
        id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.accountId = accountId;
        this.reviewerId = reviewerId;
        deadline = request.deadline();
        createdAt = now;
        state = State.OPEN;
        request.items().forEach(i -> items.add(new Item(i.field(), i.reason().trim())));
    }

    public void expire() {
        if (state == State.OPEN) state = State.EXPIRED;
    }

    public void submitted(UUID next) {
        state = State.SUBMITTED;
        submittedApplicationId = next;
    }

    @Embeddable
    @Getter
    public static class Item {
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 30)
        private SellerCorrectionField field;

        @Column(nullable = false, length = 500)
        private String reason;

        protected Item() {}

        Item(SellerCorrectionField field, String reason) {
            this.field = field;
            this.reason = reason;
        }
    }

    @Override
    public String toString() {
        return "SellerCorrectionRequest[REDACTED]";
    }

    public static class SellerCorrectionRequestBuilder {
        @Override
        public String toString() {
            return "SellerCorrectionRequestBuilder[REDACTED]";
        }
    }
}
