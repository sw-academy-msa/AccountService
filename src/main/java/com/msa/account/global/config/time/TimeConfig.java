package com.msa.account.global.config.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/** 이메일·JWT·세션 정리가 공유하는 서버 시계 설정입니다. */
@Configuration
public class TimeConfig {
    @Bean
    // 기존 빈 이름을 유지하며 테스트에서는 Clock을 대체하여 만료 경계를 검증합니다.
    public Clock accountClock() {
        return Clock.systemUTC();
    }
}
