package com.msa.account.domain.seller.query.service;

import com.msa.account.domain.seller.query.dto.response.SellerProfileResponse;
import java.util.UUID;

/** 승인된 본인의 현재 판매자 정보를 조회하는 계약입니다. 서류는 신청별 Query에서 조회합니다. */
public interface SellerProfileQueryService {
    SellerProfileResponse get(UUID accountId);
}
