package com.msa.account.domain.seller.query.service.impl;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.query.service.SellerCertificateQueryService;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import com.msa.account.domain.seller.storage.CertificateStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** 권한 검증 후 본인의 비공개 객체만 중계하며 업무 상태를 변경하지 않습니다. */
@Service
public class SellerCertificateQueryServiceImpl implements SellerCertificateQueryService {
    private final SellerAccessService access; private final SellerApplicationRepository applications;
    private final SellerCertificateRepository certificates;
    private final CertificateStorage storage;
    public SellerCertificateQueryServiceImpl(SellerAccessService access,SellerApplicationRepository applications,
            SellerCertificateRepository certificates,CertificateStorage storage) {
        this.access=access; this.applications=applications; this.certificates=certificates;
        this.storage=storage;
    }
    @Override @Transactional(readOnly=true)
    public CertificateContent own(UUID accountId,UUID applicationId) {
        access.requireBuyer(accountId,false);
        applications.findByIdAndAccount_Id(applicationId,accountId).orElseThrow(this::notFound);
        return read(applicationId);
    }
    private CertificateContent read(UUID applicationId) {
        var file=certificates.findById(applicationId).orElseThrow(this::notFound);
        return new CertificateContent(file.getFileName(),storage.get(file.getObjectKey()));
    }
    private SellerException notFound() { return new SellerException(SellerErrorCode.APPLICATION_NOT_FOUND); }
}
