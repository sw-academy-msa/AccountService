package com.msa.account.domain.seller.command.service;

import com.msa.account.domain.seller.command.dto.request.UpdateStoreRequest;
import com.msa.account.domain.seller.query.dto.response.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** 스토어 소개와 본인 로고의 저장 및 조회 계약입니다. 계좌와 입점 원본은 수정하지 않습니다. */
public interface SellerStoreService {
    StoreResponse get(UUID seller);

    StoreResponse update(UUID seller, UpdateStoreRequest request);

    StoreResponse upload(UUID seller, MultipartFile image);

    LogoContentResponse logo(UUID seller);

    void deleteLogo(UUID seller);
}
