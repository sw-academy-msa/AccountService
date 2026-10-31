package com.msa.account.domain.seller.command.service;
import java.util.UUID;
/** 운영자의 승인·거절 계약입니다. 승인과 판매자 권한 부여는 한 트랜잭션으로 처리합니다. */
public interface SellerReviewCommandService {
    void approve(UUID adminId,UUID applicationId);
    void reject(UUID adminId,UUID applicationId,String reason);
}
