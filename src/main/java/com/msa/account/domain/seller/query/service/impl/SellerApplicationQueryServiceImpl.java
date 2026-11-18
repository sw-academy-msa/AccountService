package com.msa.account.domain.seller.query.service.impl;
import com.msa.account.domain.seller.query.service.SellerApplicationQueryService;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.repository.SellerApplicationRepository;
import com.msa.account.domain.seller.repository.SellerCertificateReviewRepository;
import com.msa.account.domain.seller.service.SellerAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.*;
/** 조회에서도 현재 계정 자격을 확인하며 서류 원문을 목록에 포함하지 않습니다. */
@Service
@Transactional(readOnly=true)
public class SellerApplicationQueryServiceImpl implements SellerApplicationQueryService {
    private final SellerAccessService access; private final SellerApplicationRepository applications;
    private final SellerCertificateReviewRepository reviews;
    public SellerApplicationQueryServiceImpl(SellerAccessService access,SellerApplicationRepository applications,
            SellerCertificateReviewRepository reviews) {
        this.access=access; this.applications=applications; this.reviews=reviews;
    }
    @Override public SellerApplicationResponse latest(UUID id) {
        access.requireBuyer(id,false);
        return SellerApplicationResponse.from(applications.findFirstByAccount_IdOrderByAttemptNumberDesc(id).orElseThrow(this::notFound));
    }
    @Override public List<SellerApplicationResponse> history(UUID id) {
        access.requireBuyer(id,false);
        return applications.findAllByAccount_IdOrderByAttemptNumberDesc(id).stream().map(SellerApplicationResponse::from).toList();
    }
    @Override public SellerApplicationResponse own(UUID id,UUID applicationId) {
        access.requireBuyer(id,false);
        return SellerApplicationResponse.from(applications.findByIdAndAccount_Id(applicationId,id).orElseThrow(this::notFound));
    }
    @Override public SellerApplicationResponse detail(UUID adminId,UUID applicationId) {
        access.requireAdmin(adminId,false);
        return SellerApplicationResponse.from(applications.findById(applicationId).orElseThrow(this::notFound),
                reviews.existsByApplicationIdAndReviewerId(applicationId,adminId));
    }
    @Override public SellerApplicationPageResponse list(UUID adminId,SellerApplicationStatus status,int page,int size) {
        access.requireAdmin(adminId,false);
        var pageable=PageRequest.of(page,size,Sort.by(Sort.Order.desc("submittedAt"),Sort.Order.desc("id")));
        var results=status==null?applications.findAll(pageable):applications.findAllByStatus(status,pageable);
        return SellerApplicationPageResponse.from(results.map(SellerApplicationResponse::from));
    }
    private SellerException notFound() { return new SellerException(SellerErrorCode.APPLICATION_NOT_FOUND); }
}
