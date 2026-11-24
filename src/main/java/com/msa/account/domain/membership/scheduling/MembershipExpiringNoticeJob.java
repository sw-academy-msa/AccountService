package com.msa.account.domain.membership.scheduling;

import com.msa.account.domain.membership.service.MembershipProjectionService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 해지 예약 회원에게 혜택 종료 3일 전 알림을 한 번 만듭니다. 서버 2대가 동시에 실행해도 행 잠금으로 한 번만 기록됩니다. */
@Component
@ConditionalOnProperty(name = "account.membership.consumer-enabled", havingValue = "true")
public class MembershipExpiringNoticeJob {
    private final MembershipProjectionService service;

    public MembershipExpiringNoticeJob(MembershipProjectionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${account.membership.expiring-poll-ms:60000}")
    public void run() {
        service.notifyExpiring();
    }
}
