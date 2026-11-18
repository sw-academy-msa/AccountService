package com.msa.account.domain.account.query.service.impl;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.account.query.service.AccountSettingsQueryService;
import com.msa.account.domain.account.query.dto.response.AccountSettingsResponse;
import com.msa.account.domain.auth.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** 트랜잭션 안에서 지연 로딩을 완료하고 최신 계정 상태를 확인합니다. */
@Service
@Transactional(readOnly = true)
public class AccountSettingsQueryServiceImpl implements AccountSettingsQueryService {
    private final AccountRepository accounts;
    public AccountSettingsQueryServiceImpl(AccountRepository accounts) { this.accounts = accounts; }
    @Override
    public AccountSettingsResponse getSettings(UUID accountId) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE));
        if (account.getStatus() != AccountStatus.ACTIVE) throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        return AccountSettingsResponse.from(account);
    }
}
