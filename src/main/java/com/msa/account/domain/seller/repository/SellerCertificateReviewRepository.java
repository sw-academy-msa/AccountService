package com.msa.account.domain.seller.repository;
import com.msa.account.domain.seller.entity.SellerCertificateReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
/** 운영자의 서류 조회 여부를 신청·운영자 조합으로 확인합니다. */
public interface SellerCertificateReviewRepository extends JpaRepository<SellerCertificateReview,UUID> {
    boolean existsByApplicationIdAndReviewerId(UUID applicationId,UUID reviewerId);
}
