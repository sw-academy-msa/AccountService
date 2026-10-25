package com.msa.account.domain.seller.command.service.impl;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import com.msa.account.domain.seller.command.service.SellerCertificateReviewCommandService;
import com.msa.account.domain.seller.entity.SellerCertificateReview;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import com.msa.account.domain.seller.storage.CertificateStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;
/** 현재 운영자가 객체를 성공적으로 읽은 경우에만 열람 이력을 생성합니다. */
@Service
@Transactional
public class SellerCertificateReviewCommandServiceImpl implements SellerCertificateReviewCommandService {
    private final SellerAccessService access; private final SellerApplicationRepository applications;
    private final SellerCertificateRepository certificates; private final SellerCertificateReviewRepository reviews;
    private final CertificateStorage storage; private final Clock clock;
    public SellerCertificateReviewCommandServiceImpl(SellerAccessService access,SellerApplicationRepository applications,
            SellerCertificateRepository certificates,SellerCertificateReviewRepository reviews,CertificateStorage storage,Clock clock) {
        this.access=access; this.applications=applications; this.certificates=certificates;
        this.reviews=reviews; this.storage=storage; this.clock=clock;
    }
    @Override public CertificateContent review(UUID adminId,UUID applicationId) {
        // 같은 운영자의 동시 열람도 직렬화하여 이력이 중복 저장되지 않게 합니다.
        access.requireAdmin(adminId,true);
        applications.findById(applicationId).orElseThrow(this::notFound);
        var file=certificates.findById(applicationId).orElseThrow(this::notFound);
        var content=new CertificateContent(file.getFileName(),storage.get(file.getObjectKey()));
        if(!reviews.existsByApplicationIdAndReviewerId(applicationId,adminId))
            reviews.save(new SellerCertificateReview(applicationId,adminId,clock.instant()));
        return content;
    }
    private SellerException notFound() { return new SellerException(SellerErrorCode.APPLICATION_NOT_FOUND); }
}
