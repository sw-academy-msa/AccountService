package com.msa.account.domain.seller.command.service;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import java.util.UUID;
/** PDF를 반환하면서 심사 자격에 영향을 주는 열람 기록을 남기므로 Command로 분리합니다. */
public interface SellerCertificateReviewCommandService {
    CertificateContent review(UUID adminId,UUID applicationId);
}
