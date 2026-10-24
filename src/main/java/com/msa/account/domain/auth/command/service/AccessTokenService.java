package com.msa.account.domain.auth.command.service;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.auth.command.dto.response.TokenResponse;

/** 다른 서비스가 독립적으로 검증할 수 있는 Access JWT 발급 계약입니다. */
public interface AccessTokenService {
    TokenResponse issue(Account account);
}
