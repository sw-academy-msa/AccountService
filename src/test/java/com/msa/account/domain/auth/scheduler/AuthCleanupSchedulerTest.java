package com.msa.account.domain.auth.scheduler;

import com.msa.account.domain.auth.command.service.AuthCleanupService;
import com.msa.account.global.config.scheduling.AuthCleanupProperties;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 대량 적체와 일시 실패 상황에서 예약 작업이 무제한 실행되지 않는지 검증합니다. */
class AuthCleanupSchedulerTest {
    private final AuthCleanupProperties properties = new AuthCleanupProperties(Duration.ofDays(7), 2, 3);

    @Test
    // 매번 삭제할 데이터가 있더라도 한 번의 예약 실행은 설정한 배치 수까지만 처리합니다.
    void stopsAtConfiguredBatchLimit() {
        var service = mock(AuthCleanupService.class);
        when(service.deleteExpiredBatch()).thenReturn(2);
        new AuthCleanupScheduler(service, properties).cleanExpiredSessions();
        verify(service, times(3)).deleteExpiredBatch();
    }

    @Test
    // 한 실행이 실패해도 이후 예약 실행에서는 다시 정리를 시도할 수 있습니다.
    void retriesOnNextScheduledRunAfterFailure() {
        var service = mock(AuthCleanupService.class);
        when(service.deleteExpiredBatch()).thenThrow(new IllegalStateException("test failure")).thenReturn(2, 0);
        var scheduler = new AuthCleanupScheduler(service, properties);
        assertThatCode(scheduler::cleanExpiredSessions).doesNotThrowAnyException();
        scheduler.cleanExpiredSessions();
        verify(service, times(3)).deleteExpiredBatch();
    }
}
