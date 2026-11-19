package com.msa.account.domain.support.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 운영팀 1:1 문의입니다. 주문 문의(판매자 답변)와 달리 운영자가 한 번 답변합니다. */
@Entity
@Table(schema = "account", name = "support_inquiries")
public class SupportInquiry {
    public enum Category { ACCOUNT, PAYMENT, APP_ERROR, SELLING, ETC }

    public enum Status { WAITING, ANSWERED }

    @Id private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Category category;

    @Column(nullable = false, length = 100, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 2000)
    private String answer;

    @Column(name = "answered_by")
    private UUID answeredBy;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version private long version;

    protected SupportInquiry() {}

    public SupportInquiry(
            UUID accountId, Category category, String title, String body, UUID orderId, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.orderId = orderId;
        this.status = Status.WAITING;
        this.createdAt = now;
    }

    /** 답변은 한 번만 등록합니다(같은 내용으로 다시 보내면 그대로 둡니다). */
    public boolean answer(UUID admin, String text, Instant now) {
        if (status == Status.ANSWERED) return false;
        this.answer = text;
        this.answeredBy = admin;
        this.answeredAt = now;
        this.status = Status.ANSWERED;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public UUID getOrderId() { return orderId; }
    public Status getStatus() { return status; }
    public String getAnswer() { return answer; }
    public Instant getAnsweredAt() { return answeredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
