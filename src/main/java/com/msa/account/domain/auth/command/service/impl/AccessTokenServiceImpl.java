package com.msa.account.domain.auth.command.service.impl;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.auth.command.dto.response.IssuedJwt;
import com.msa.account.domain.auth.command.dto.response.ServiceTokenResponse;
import com.msa.account.domain.auth.command.dto.response.TokenResponse;
import com.msa.account.domain.auth.command.service.AccessTokenService;
import com.msa.account.global.config.security.AuthProperties;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/** 이메일·이름·배송지 등 개인정보 없이 식별자와 권한만 서명합니다. JWT는 암호화가 아닙니다. */
@Service
public class AccessTokenServiceImpl implements AccessTokenService {
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final RSAKey key;
    private final Clock clock;

    public AccessTokenServiceImpl(
            JwtEncoder encoder, AuthProperties properties, RSAKey key, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.key = key;
        this.clock = clock;
    }

    @Override
    // sub는 accountId이며 roles는 발급 시점의 스냅샷입니다.
    public TokenResponse issue(Account account) {
        Set<String> roles =
                account.getRoles().stream()
                        .map(Enum::name)
                        .collect(Collectors.toCollection(TreeSet::new));
        var signed =
                sign(
                        account.getId(),
                        roles,
                        Map.of("principal_type", "user"),
                        properties.accessTtl());
        return TokenResponse.builder()
                .accessToken(signed.value())
                .tokenType("Bearer")
                .expiresAt(signed.expiresAt())
                .accountId(account.getId())
                .roles(roles)
                .build();
    }

    @Override
    public ServiceTokenResponse issueService(
            UUID subject, String clientId, Set<String> scopes, java.time.Duration ttl) {
        String scope = String.join(" ", new TreeSet<>(scopes));
        var signed =
                sign(
                        subject,
                        Set.of(),
                        Map.of("principal_type", "service", "client_id", clientId, "scope", scope),
                        ttl);
        return ServiceTokenResponse.builder()
                .accessToken(signed.value())
                .tokenType("Bearer")
                .expiresIn(ttl.toSeconds())
                .expiresAt(signed.expiresAt())
                .scope(scope)
                .build();
    }

    /** 키·kid·RS256·issuer/audience·시간·jti 구성은 사용자와 서비스가 공유합니다. */
    private IssuedJwt sign(
            UUID subject, Set<String> roles, Map<String, Object> extra, java.time.Duration ttl) {
        var now = clock.instant();
        var expiry = now.plus(ttl);
        var builder =
                JwtClaimsSet.builder()
                        .issuer(properties.issuer())
                        .subject(subject.toString())
                        .audience(List.of(properties.audience()))
                        .issuedAt(now)
                        .expiresAt(expiry)
                        .id(UUID.randomUUID().toString())
                        .claim("roles", roles)
                        .claim("token_use", "access");
        extra.forEach(builder::claim);
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        String token =
                encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
        return new IssuedJwt(token, expiry);
    }
}
