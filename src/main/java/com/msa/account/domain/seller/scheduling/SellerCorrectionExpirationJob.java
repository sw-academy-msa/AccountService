package com.msa.account.domain.seller.scheduling;

import com.msa.account.domain.seller.command.service.SellerApplicationRevisionService;
import com.msa.account.domain.seller.repository.SellerCorrectionRequestRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 기한 지난 보완을 작은 배치로 정리합니다. 각 항목은 서비스의 독립 트랜잭션으로 재확인합니다. */
@Component
@ConditionalOnProperty(
        name = "account.seller.corrections.expiration-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SellerCorrectionExpirationJob {
    private final SellerCorrectionRequestRepository repository;
    private final SellerApplicationRevisionService service;
    private final Clock clock;

    public SellerCorrectionExpirationJob(
            SellerCorrectionRequestRepository repository,
            SellerApplicationRevisionService service,
            Clock clock) {
        this.repository = repository;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${account.seller.corrections.poll-ms:60000}")
    public void expire() {
        for (var id :
                repository.findExpired(
                        com.msa.account.domain.seller.entity.SellerCorrectionRequest.State.OPEN,
                        clock.instant(),
                        PageRequest.of(0, 100))) service.expire(id);
    }
}
