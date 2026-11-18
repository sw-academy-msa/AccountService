package com.msa.account.domain.seller.command.service;

import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.command.dto.response.SellerApplicationCreatedResponse;
import com.msa.account.domain.seller.query.dto.response.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** 보완·PDF 교체는 새 신청 회차를 만들고 기존 승인·열람 이력과 파일을 보존합니다. */
public interface SellerApplicationRevisionService {
    SellerCorrectionResponse requestCorrections(
            UUID admin, UUID application, RequestSellerCorrections request);

    SellerCorrectionResponse correction(UUID actor, UUID application, boolean admin);

    SellerApplicationCreatedResponse resubmit(
            UUID owner,
            UUID application,
            SellerCorrectionSubmissionRequest request,
            List<MultipartFile> files);

    SellerApplicationCreatedResponse recheck(UUID owner, List<MultipartFile> files);

    SellerReviewStateResponse state(UUID owner);

    void expire(UUID correctionId);
}
