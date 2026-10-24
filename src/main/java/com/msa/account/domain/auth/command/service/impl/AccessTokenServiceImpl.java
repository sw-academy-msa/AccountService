package com.msa.account.domain.auth.command.service.impl;

import com.msa.account.domain.account.entity.Account;
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
    public AccessTokenServiceImpl(JwtEncoder encoder, AuthProperties properties, RSAKey key, Clock clock) {
        this.encoder = encoder; this.properties = properties; this.key = key; this.clock = clock;
    }
    @Override
    // sub는 accountId이며 roles는 발급 시점의 스냅샷입니다.
    public TokenResponse issue(Account account) {
        var now = clock.instant();
        var expiry = now.plus(properties.accessTtl());
        Set<String> roles = account.getRoles().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(account.getId().toString())
                .audience(List.of(properties.audience())).issuedAt(now).expiresAt(expiry)
                .id(UUID.randomUUID().toString()).claim("roles", roles).claim("token_use", "access").build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiry, account.getId(), roles);
    }
}
