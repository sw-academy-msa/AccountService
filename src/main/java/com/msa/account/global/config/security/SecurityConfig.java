package com.msa.account.global.config.security;

import com.msa.account.global.error.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** Access JWT는 Bearer 헤더로 검증하고 Refresh 쿠키를 쓰는 인증 API에는 CSRF 보호를 적용합니다. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties properties,
            CookieCsrfTokenRepository csrfRepository, ObjectMapper mapper) throws Exception {
        var roleConverter = new JwtGrantedAuthoritiesConverter();
        roleConverter.setAuthoritiesClaimName("roles");
        roleConverter.setAuthorityPrefix("ROLE_");
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(roleConverter);
        Set<String> cookieEndpoints = Set.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        // JSON으로 받은 원문 CSRF 토큰을 헤더로 전송하는 SPA 방식입니다.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> "POST".equals(request.getMethod())
                                && cookieEndpoints.contains(request.getRequestURI().substring(request.getContextPath().length()))))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> error(response, CommonErrorCode.UNAUTHORIZED, mapper))
                        .accessDeniedHandler((request, response, exception) -> error(response,
                                exception instanceof CsrfException ? CommonErrorCode.CSRF_REJECTED : CommonErrorCode.FORBIDDEN, mapper)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/api/v1/auth/signup", "/api/v1/auth/email-verifications",
                                "/api/v1/auth/email-verifications/confirm", "/api/v1/signup-options",
                                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/csrf", "/.well-known/jwks.json",
                                "/api/v1/auth/password-resets", "/api/v1/auth/password-resets/confirm",
                                "/api/v1/auth/password-resets/complete",
                                "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint((request, response, exception) -> error(response, CommonErrorCode.UNAUTHORIZED, mapper)))
                .build();
    }

    @Bean
    // CSRF 쿠키도 호스트 전용이며 프론트는 JSON 응답에서 헤더 값을 읽습니다.
    public CookieCsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName(properties.csrfCookieName());
        repository.setCookieCustomizer(cookie -> cookie.path("/").httpOnly(true)
                .secure(properties.cookieSecure()).sameSite(properties.cookieSameSite()));
        return repository;
    }

    // 자격증명 허용 CORS는 명시한 프론트 Origin에만 적용합니다. Postman은 CORS 대상이 아닙니다.
    private CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // 필터 예외는 ControllerAdvice에 도달하지 않으므로 같은 오류 형식을 직접 작성합니다.
    private void error(HttpServletResponse response, ErrorCode code, ObjectMapper mapper) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, MDC.get("traceId")));
    }

    @Bean
    // BCrypt 비용 계수는 기존 회원가입과 동일하게 유지합니다.
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
