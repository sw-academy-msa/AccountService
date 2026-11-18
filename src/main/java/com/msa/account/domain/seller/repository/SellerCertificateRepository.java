package com.msa.account.domain.seller.repository;
import com.msa.account.domain.seller.entity.SellerCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
/** 인증된 서류 조회 서비스에서만 PDF 원문에 접근합니다. */
public interface SellerCertificateRepository extends JpaRepository<SellerCertificate,UUID> { }
