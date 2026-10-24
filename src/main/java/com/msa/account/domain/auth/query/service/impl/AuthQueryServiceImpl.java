package com.msa.account.domain.auth.query.service.impl;

import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.auth.exception.*;
import com.msa.account.domain.auth.query.dto.response.CurrentAccountResponse;
import com.msa.account.domain.auth.query.service.AuthQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.stream.Collectors;

/** 본인 조회에서는 JWT 발급 이후 변경한 계정 상태까지 원본 DB로 확인합니다. */
@Service
@Transactional(readOnly = true)
public class AuthQueryServiceImpl implements AuthQueryService {
    private final AccountRepository accounts;
    public AuthQueryServiceImpl(AccountRepository accounts) { this.accounts = accounts; }
    @Override
    public CurrentAccountResponse currentAccount(UUID id) {
        var account = accounts.findById(id).orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE));
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        return new CurrentAccountResponse(account.getId(), account.getEmail(), account.getName(),
                account.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
    }
}
