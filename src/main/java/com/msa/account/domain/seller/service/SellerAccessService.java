package com.msa.account.domain.seller.service;
import com.msa.account.domain.account.entity.Account;
import java.util.UUID;
/** 신청·심사 트랜잭션 안에서 DB의 최신 계정 자격을 검증하는 계약입니다. */
public interface SellerAccessService {
    Account requireBuyer(UUID id,boolean lock);
    Account requireSeller(UUID id,boolean lock);
    Account requireAdmin(UUID id,boolean lock);
}
