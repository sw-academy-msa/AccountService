package com.msa.account.domain.auth.command.service;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.auth.command.dto.response.TokenResponse;

/** 다른 서비스가 독립적으로 검증할 수 있는 Access JWT 발급 계약입니다. */
public interface AccessTokenService {
    TokenResponse issue(Account account);

    // 기존 서명 경로를 재사용하며 허용 주체/권한은 서비스 자격증명 검증을 마친 호출자만 전달합니다.
    com.msa.account.domain.auth.command.dto.response.ServiceTokenResponse issueService(
            java.util.UUID subject,
            String clientId,
            java.util.Set<String> scopes,
            java.time.Duration ttl);
}
