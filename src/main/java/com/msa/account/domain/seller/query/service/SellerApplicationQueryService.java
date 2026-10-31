package com.msa.account.domain.seller.query.service;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import java.util.*;
/** 신청자 본인 조회와 운영자 심사 조회를 구분한 계약입니다. */
public interface SellerApplicationQueryService {
    SellerApplicationResponse latest(UUID accountId);
    List<SellerApplicationResponse> history(UUID accountId);
    SellerApplicationResponse own(UUID accountId,UUID applicationId);
    SellerApplicationResponse detail(UUID adminId,UUID applicationId);
    SellerApplicationPageResponse list(UUID adminId,SellerApplicationStatus status,int page,int size);
}
