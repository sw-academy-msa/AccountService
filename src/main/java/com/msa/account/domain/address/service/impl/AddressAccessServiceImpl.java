package com.msa.account.domain.address.service.impl;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.address.exception.*;
import com.msa.account.domain.address.service.AddressAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/** 상위 업무 트랜잭션 안에서 현재 권한을 확인하며 쓰기는 항상 계정부터 잠급니다. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AddressAccessServiceImpl implements AddressAccessService {
    private final AccountRepository accounts;
    public AddressAccessServiceImpl(AccountRepository accounts) { this.accounts = accounts; }

    @Override
    public Account requireBuyer(UUID id) {
        return check(accounts.findById(id).orElseThrow(this::denied));
    }
    @Override
    public Account requireBuyerForUpdate(UUID id) {
        // A/B 복제 인스턴스의 첫 등록·기본 지정·삭제도 같은 DB 잠금으로 직렬화합니다.
        return check(accounts.findLockedById(id).orElseThrow(this::denied));
    }
    private Account check(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE || account.getEmailVerifiedAt() == null || !account.hasRole(Role.BUYER))
            throw denied();
        return account;
    }
    private AddressException denied() { return new AddressException(AddressErrorCode.BUYER_REQUIRED); }
}
