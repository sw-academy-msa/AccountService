package com.msa.account.global.config.security;

import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.time.*;
import java.util.*;

/** 사용자 JwtDecoder를 완화하지 않습니다. 지정 Notification 주체의 제한된 scope만 내부 체인에서 허용합니다. */
@Configuration
public class NotificationEligibilitySecurityConfig {
    @Bean
    @Order(2)
    public SecurityFilterChain notificationEligibility(
            HttpSecurity http,
            RSAKey key,
            AuthProperties auth,
            ServiceAuthProperties services,
            Clock clock)
            throws Exception {
        var decoder =
                NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        var time = new JwtTimestampValidator(Duration.ofSeconds(30));
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> contract =
                jwt -> {
                    var client = services.clients().get("notification-service");
                    String scopes = jwt.getClaimAsString("scope");
                    boolean valid =
                            services.enabled()
                                    && client != null
                                    && client.subject() != null
                                    && client.subject().toString().equals(jwt.getSubject())
                                    && "notification-service"
                                            .equals(jwt.getClaimAsString("client_id"))
                                    && "service".equals(jwt.getClaimAsString("principal_type"))
                                    && jwt.getAudience() != null
                                    && jwt.getAudience().contains(auth.audience())
                                    && "access".equals(jwt.getClaimAsString("token_use"))
                                    && jwt.getExpiresAt() != null
                                    && scopes != null
                                    && Arrays.asList(scopes.split(" +"))
                                            .contains("account.notifications.read");
                    return valid
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                };
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        time, new JwtIssuerValidator(auth.issuer()), contract));
        return http.securityMatcher("/internal/v1/notification-eligibility")
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .logout(c -> c.disable())
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                HttpMethod.GET,
                                                "/internal/v1/notification-eligibility")
                                        .hasAuthority("SCOPE_account.notifications.read")
                                        .anyRequest()
                                        .denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)))
                .build();
    }
}
