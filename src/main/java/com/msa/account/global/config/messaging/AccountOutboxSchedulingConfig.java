package com.msa.account.global.config.messaging;

import com.msa.account.domain.outbox.service.OutboxPublisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** 발행 작업은 Account 내부에서 실행합니다. 별도 Outbox 서버를 만들지 않습니다. */
@Configuration
@ConditionalOnProperty(name = "account.outbox.publisher-enabled", havingValue = "true")
public class AccountOutboxSchedulingConfig {
    private final OutboxPublisher publisher;

    public AccountOutboxSchedulingConfig(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${account.outbox.poll-ms:1000}")
    public void publish() {
        publisher.publishNext();
    }
}
