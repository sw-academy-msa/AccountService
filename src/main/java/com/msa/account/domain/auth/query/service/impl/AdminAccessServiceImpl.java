package com.msa.account.domain.auth.query.service.impl;

import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.query.service.AdminAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** 캐시 없이 DB 상태를 조회하여 직접 DB에서 변경한 권한 회수와 정지도 반영합니다. */
@Service
@Transactional(readOnly = true)
public class AdminAccessServiceImpl implements AdminAccessService {
    private final AccountRepository accounts;

    public AdminAccessServiceImpl(AccountRepository accounts) { this.accounts = accounts; }

    @Override
    public boolean isCurrentAdmin(String subject) {
        // 잘못된 사용자 식별자를 업무 조회에 사용하지 않습니다. DB 장애는 성공으로 대체하지 않습니다.
        UUID id;
        try { id = UUID.fromString(subject); }
        catch (IllegalArgumentException | NullPointerException exception) { return false; }
        return accounts.hasCurrentRole(id, AccountStatus.ACTIVE, Role.ADMIN);
    }
}
