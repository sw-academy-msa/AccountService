package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.domain.verification.enums.VerificationPurpose;
import org.junit.jupiter.api.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실제 SMTP 없이 커밋 이후 발송·롤백·미가입 주소·작업 큐 장애를 검증합니다. */
class PasswordResetMailDispatcherImplTest {
    private final Instant now = Instant.parse("2026-10-24T00:00:00Z");
    private final TaskExecutor executor = mock(TaskExecutor.class);
    private final VerificationMailSender sender = mock(VerificationMailSender.class);
    private final PasswordResetMailDispatcherImpl dispatcher = new PasswordResetMailDispatcherImpl(
            executor, sender, Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    // 동기화 콜백을 직접 제어해 실제 커밋과 롤백의 차이를 재현합니다.
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(executor).execute(any(Runnable.class));
    }
    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    // 커밋 전에는 메일을 보내지 않고 커밋된 뒤 목적이 PASSWORD_RESET인 메일만 발송합니다.
    void sendsOnlyAfterCommit() {
        dispatcher.dispatchAfterCommit("buyer@example.com", "001234", now.plusSeconds(300), true);
        verifyNoInteractions(executor, sender);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sender).send("buyer@example.com", "001234", now.plusSeconds(300), VerificationPurpose.PASSWORD_RESET);
    }

    @Test
    // 롤백은 발송 작업을 등록하지 않으므로 DB에 없는 번호가 메일로 나가지 않습니다.
    void doesNotSendOnRollback() {
        dispatcher.dispatchAfterCommit("buyer@example.com", "001234", now.plusSeconds(300), true);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(executor, sender);
    }

    @Test
    // 미가입 주소와 이미 만료된 작업도 같은 큐 경로를 거치지만 실제 메일은 보내지 않습니다.
    void ignoresIneligibleAndExpiredJobsInsideWorker() {
        dispatcher.dispatchAfterCommit("missing@example.com", "001234", now.plusSeconds(300), false);
        dispatcher.dispatchAfterCommit("buyer@example.com", "001234", now, true);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(executor, times(2)).execute(any(Runnable.class));
        verifyNoInteractions(sender);
    }

    @Test
    // 큐가 가득 차도 커밋된 공개 API의 응답이 가입 여부에 따라 달라지지 않습니다.
    void handlesQueueRejectionWithoutLeakingRecipient() {
        doThrow(new TaskRejectedException("test full queue")).when(executor).execute(any(Runnable.class));
        dispatcher.dispatchAfterCommit("buyer@example.com", "001234", now.plusSeconds(300), true);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verifyNoInteractions(sender);
    }

    @Test
    // SMTP 실패는 작업 안에서 추적하며 응답이나 로그에 수신자와 번호를 직접 남기지 않습니다.
    void handlesDeliveryFailureInsideWorker() {
        doThrow(new IllegalStateException("test delivery failure")).when(sender)
                .send(anyString(), anyString(), any(Instant.class), eq(VerificationPurpose.PASSWORD_RESET));
        dispatcher.dispatchAfterCommit("buyer@example.com", "001234", now.plusSeconds(300), true);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
