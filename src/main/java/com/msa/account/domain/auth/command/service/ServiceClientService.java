package com.msa.account.domain.auth.command.service;

import com.msa.account.domain.auth.command.dto.request.ServiceTokenRequest;
import com.msa.account.domain.auth.command.dto.response.*;

/** 서비스 자격증명과 scope만 검사하며 실제 JWT 서명은 기존 AccessTokenService를 재사용합니다. */
public interface ServiceClientService {
    ServiceClientPrincipal authenticate(String clientId, String secret);

    ServiceTokenResponse issue(ServiceClientPrincipal principal, ServiceTokenRequest request);
}
