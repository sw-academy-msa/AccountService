package com.msa.account.domain.seller.entity;
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
    public SellerCertificateReview(UUID applicationId,UUID reviewerId,Instant now) {
        this.applicationId=applicationId; this.reviewerId=reviewerId; viewedAt=now;
    }
}
