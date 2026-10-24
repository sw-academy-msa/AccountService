package com.msa.account.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP 접근 정책과 비밀번호 해시 방식을 설정합니다. 세션 대신 토큰을 사용하는 구조의 기본 보안 설정입니다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    // 공개 경로만 인증 없이 허용하고 나머지는 인증을 요구합니다. JWT 검증 연결은 후속 인증 구현에서 추가합니다.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 현재 세션을 사용하지 않는 API 설정입니다. 인증 쿠키 도입 시 CSRF 정책을 재검토해야 합니다.
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/email-verifications",
                                "/api/v1/auth/email-verifications/confirm",
                                "/api/v1/signup-options",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    // 단방향 BCrypt 해시를 생성합니다. 비용 계수 12는 해시 계산량을 결정합니다.
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
