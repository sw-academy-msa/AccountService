package com.msa.account.domain.auth.command.service.impl;

import com.msa.account.domain.auth.command.dto.request.ServiceTokenRequest;
import com.msa.account.domain.auth.command.dto.response.*;
import com.msa.account.domain.auth.command.service.*;
import com.msa.account.global.config.security.*;
import com.msa.account.global.error.*;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 기존 PasswordEncoder를 공유합니다. 서비스 인증에 사용자 로그인/Refresh 저장소를 사용하지 않습니다. */
@Service
public class ServiceClientServiceImpl implements ServiceClientService {
    private final PasswordEncoder passwords;
    private final ServiceAuthProperties properties;
    private final AuthProperties auth;
    private final AccessTokenService tokens;
    private final String dummyHash;

    public ServiceClientServiceImpl(
            PasswordEncoder passwords,
            ServiceAuthProperties properties,
            AuthProperties auth,
            AccessTokenService tokens) {
        this.passwords = passwords;
        this.properties = properties;
        this.auth = auth;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public ServiceClientPrincipal authenticate(String id, String secret) {
        var client = id == null ? null : properties.clients().get(id);
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length > 72
                || id == null
                || id.length() > 100) {
            throw new BadCredentialsException("서비스 인증이 필요합니다.");
        }
        boolean match =
                passwords.matches(
                        secret,
                        properties.enabled() && client != null ? client.secretHash() : dummyHash);
        if (!properties.enabled() || client == null || !match) {
            throw new BadCredentialsException("서비스 인증이 필요합니다.");
        }
        return new ServiceClientPrincipal(client.subject(), id, client.scopes());
    }

    public ServiceTokenResponse issue(ServiceClientPrincipal principal, ServiceTokenRequest r) {
        if (!properties.enabled()
                || principal == null
                || !"client_credentials".equals(r.grantType())
                || r.scope() != null && r.scope().length() > 200) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        var configured = properties.clients().get(principal.clientId());
        if (configured == null || !configured.subject().equals(principal.subject())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        Set<String> requested =
                r.scope() == null || r.scope().isBlank()
                        ? configured.scopes()
                        : new TreeSet<>(Arrays.asList(r.scope().trim().split(" +")));
        if (requested.isEmpty() || !configured.scopes().containsAll(requested)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        var ttl =
                properties.tokenTtl().compareTo(auth.accessTtl()) > 0
                        ? auth.accessTtl()
                        : properties.tokenTtl();
        return tokens.issueService(configured.subject(), principal.clientId(), requested, ttl);
    }
}
