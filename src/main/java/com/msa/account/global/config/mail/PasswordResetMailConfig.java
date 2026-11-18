package com.msa.account.global.config.mail;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** SMTP 응답 시간이 공개 API의 가입 여부 신호가 되지 않도록 재설정 메일만 비동기로 처리합니다. */
@Configuration
public class PasswordResetMailConfig {
    @Bean("passwordResetMailExecutor")
    // 메모리 사용량을 제한합니다. 영속 큐가 아니므로 재시작·큐 초과 시 사용자가 재요청해야 합니다.
    public ThreadPoolTaskExecutor passwordResetMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-reset-mail-");
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
