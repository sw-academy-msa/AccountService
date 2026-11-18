package com.msa.account.domain.auth.scheduler;

import com.msa.account.domain.auth.command.service.AuthCleanupService;
import com.msa.account.global.config.scheduling.AuthCleanupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 새벽 4시 한국 시간에 보관 기간이 지난 인증 기록을 제한된 배치로 정리합니다. */
@Component
@ConditionalOnProperty(prefix = "account.auth.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuthCleanupScheduler.class);
    private final AuthCleanupService service;
    private final AuthCleanupProperties properties;

    public AuthCleanupScheduler(AuthCleanupService service, AuthCleanupProperties properties) {
        this.service = service; this.properties = properties;
    }

    @Scheduled(cron = "${account.auth.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    // 반복마다 서비스 프록시를 호출하여 긴 단일 트랜잭션 대신 배치별로 커밋합니다.
    public void cleanExpiredSessions() {
        int deleted = 0;
        try {
            for (int batch = 0; batch < properties.maxBatches(); batch++) {
                int count = service.deleteExpiredBatch();
                deleted += count;
                if (count == 0) break;
            }
            log.info("Authentication cleanup completed: deletedSessions={}", deleted);
        } catch (RuntimeException exception) {
            // 개인정보·토큰은 출력하지 않으며 실패한 배치는 롤백되고 다음 예약 실행에서 재시도합니다.
            log.error("Authentication cleanup failed: committedSessions={}", deleted, exception);
        }
    }
}
