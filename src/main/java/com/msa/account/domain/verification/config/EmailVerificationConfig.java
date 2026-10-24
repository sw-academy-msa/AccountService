package com.msa.account.domain.verification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/** 인증 정책을 등록하고 테스트에서 교체할 수 있는 서버 시계를 제공합니다. */
@Configuration
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig {
    @Bean
    // DB 시간 저장과 유효 시간 비교는 UTC Instant를 사용합니다.
    public Clock accountClock() {
        return Clock.systemUTC();
    }
}
