package com.msa.account.domain.seller.service.impl;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
/** JWT의 권한이 남아 있어도 정지·권한 회수된 계정은 민감 정보를 조회할 수 없습니다. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class SellerAccessServiceImpl implements SellerAccessService {
    private final AccountRepository accounts;
    public SellerAccessServiceImpl(AccountRepository accounts) { this.accounts=accounts; }
    @Override public Account requireBuyer(UUID id,boolean lock) { return require(id,lock,Role.BUYER,SellerErrorCode.BUYER_REQUIRED); }
    @Override public Account requireSeller(UUID id,boolean lock) { return require(id,lock,Role.SELLER,SellerErrorCode.SELLER_REQUIRED); }
    @Override public Account requireAdmin(UUID id,boolean lock) { return require(id,lock,Role.ADMIN,SellerErrorCode.ADMIN_REQUIRED); }
    private Account require(UUID id,boolean lock,Role role,SellerErrorCode error) {
        var account=(lock?accounts.findLockedById(id):accounts.findById(id)).orElseThrow(()->new SellerException(error));
        if(account.getStatus()!=AccountStatus.ACTIVE || account.getEmailVerifiedAt()==null || !account.hasRole(role))
            throw new SellerException(error);
        return account;
    }
}
