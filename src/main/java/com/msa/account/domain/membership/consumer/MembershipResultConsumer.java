package com.msa.account.domain.membership.consumer;

import com.msa.account.domain.membership.service.MembershipProjectionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 멤버십 전용 유한 재시도와 DLT를 사용하며 projection 커밋 후 offset을 진행합니다. */
@Component
@ConditionalOnProperty(name = "account.membership.consumer-enabled", havingValue = "true")
public class MembershipResultConsumer {
    private final MembershipProjectionService service;

    public MembershipResultConsumer(MembershipProjectionService service) {
        this.service = service;
    }

    @KafkaListener(
            id = "membership-results",
            groupId = "account-membership-result-v1",
            topics = "stockdrop.membership.events.private.v1",
            containerFactory = "membershipKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, byte[]> record) {
        service.receive(record.key(), record.value());
    }
}
