package com.msa.account.domain.seller.entity;

import lombok.Builder;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;
/** 운영자별 PDF 조회 이력입니다. 문서 내용을 실제로 읽었다는 사실까지 증명하지 않습니다. */
@Entity
@Table(schema="account",name="seller_certificate_reviews",
        uniqueConstraints=@UniqueConstraint(name="uk_seller_certificate_reviewer",columnNames={"application_id","reviewer_id"}))
public class SellerCertificateReview {
    @Id @UuidGenerator private UUID id;
    @Column(name="application_id",nullable=false,updatable=false) private UUID applicationId;
    @Column(name="reviewer_id",nullable=false,updatable=false) private UUID reviewerId;
    @Column(name="viewed_at",nullable=false) private Instant viewedAt;
    protected SellerCertificateReview() { }
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder
    public SellerCertificateReview(UUID applicationId,UUID reviewerId,Instant now) {
        this.applicationId=applicationId; this.reviewerId=reviewerId; viewedAt=now;
    }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class SellerCertificateReviewBuilder {
        @Override
        public String toString() {
            return "SellerCertificateReviewBuilder[REDACTED]";
        }
    }
}
