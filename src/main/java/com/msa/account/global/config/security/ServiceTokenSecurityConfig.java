package com.msa.account.global.config.security;

import com.msa.account.domain.auth.command.service.ServiceClientService;
import com.msa.account.global.error.*;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** 내부 토큰 경로에만 Basic 인증을 적용합니다. 기존 사용자 Bearer/Refresh/CSRF 필터 체인은 유지합니다. */
@Configuration
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class ServiceTokenSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain serviceTokens(
            HttpSecurity http,
            ServiceClientService clients,
            ObjectMapper mapper,
            org.springframework.core.env.Environment environment)
            throws Exception {
        AuthenticationProvider provider =
                new AuthenticationProvider() {
                    public boolean supports(Class<?> type) {
                        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
                    }

                    public Authentication authenticate(Authentication authentication) {
                        var principal =
                                clients.authenticate(
                                        authentication.getName(),
                                        authentication.getCredentials() == null
                                                ? null
                                                : authentication.getCredentials().toString());
                        return UsernamePasswordAuthenticationToken.authenticated(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("SERVICE_CLIENT")));
                    }
                };
        org.springframework.security.web.AuthenticationEntryPoint denied =
                (req, res, ex) -> {
                    res.setStatus(401);
                    res.setHeader("WWW-Authenticate", "Basic realm=\"stockdrop-services\"");
                    res.setHeader("Cache-Control", "no-store");
                    res.setContentType("application/json");
                    res.setCharacterEncoding("UTF-8");
                    mapper.writeValue(
                            res.getOutputStream(),
                            ErrorResponse.of(
                                    CommonErrorCode.UNAUTHORIZED, org.slf4j.MDC.get("traceId")));
                };
        var transportGuard =
                new org.springframework.web.filter.OncePerRequestFilter() {
                    protected void doFilterInternal(
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response,
                            jakarta.servlet.FilterChain chain)
                            throws jakarta.servlet.ServletException, java.io.IOException {
                        if (environment.acceptsProfiles(
                                        org.springframework.core.env.Profiles.of("prod"))
                                && !request.isSecure()) {
                            denied.commence(
                                    request,
                                    response,
                                    new InsufficientAuthenticationException("TLS가 필요합니다."));
                            return;
                        }
                        chain.doFilter(request, response);
                    }
                };
        return http.securityMatcher("/internal/v1/auth/service-tokens")
                .addFilterBefore(
                        transportGuard,
                        org.springframework.security.web.authentication.www
                                .BasicAuthenticationFilter.class)
                .authenticationManager(new ProviderManager(provider))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                HttpMethod.POST, "/internal/v1/auth/service-tokens")
                                        .hasAuthority("SERVICE_CLIENT")
                                        .anyRequest()
                                        .denyAll())
                .httpBasic(b -> b.authenticationEntryPoint(denied))
                .exceptionHandling(e -> e.authenticationEntryPoint(denied))
                .build();
    }
}
