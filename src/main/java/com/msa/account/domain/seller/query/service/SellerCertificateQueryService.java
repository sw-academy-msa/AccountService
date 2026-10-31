package com.msa.account.domain.seller.query.service;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import java.util.UUID;
/** 본인 서류 조회 계약입니다. 승인에 영향을 주는 운영자 열람 기록은 Command가 담당합니다. */
public interface SellerCertificateQueryService {
    CertificateContent own(UUID accountId,UUID applicationId);
}
