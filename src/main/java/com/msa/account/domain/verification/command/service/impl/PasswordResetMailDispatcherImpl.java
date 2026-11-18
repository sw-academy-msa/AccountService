package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.*;
import com.msa.account.domain.verification.enums.VerificationPurpose;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import java.time.*;

/** DB 롤백 시 발송하지 않으며 HTTP 응답에서는 SMTP 성공·실패와 가입 여부를 노출하지 않습니다. */
@Service
public class PasswordResetMailDispatcherImpl implements PasswordResetMailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailDispatcherImpl.class);
    private final TaskExecutor executor;
    private final VerificationMailSender sender;
    private final Clock clock;
    public PasswordResetMailDispatcherImpl(@Qualifier("passwordResetMailExecutor") TaskExecutor executor,
            VerificationMailSender sender, Clock clock) {
        this.executor = executor; this.sender = sender; this.clock = clock;
    }
    @Override
    public void dispatchAfterCommit(String email, String code, Instant expiresAt, boolean eligible) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("재설정 메일은 DB 트랜잭션 안에서 예약해야 합니다.");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 미가입 주소도 같은 큐 등록 경로를 거치며 수신 대상 판정은 작업 스레드 안에서 합니다.
                    executor.execute(() -> deliver(email, code, expiresAt, eligible));
                } catch (TaskRejectedException exception) {
                    log.warn("Password reset mail queue rejected a task; requester may retry");
                }
            }
        });
    }
    private void deliver(String email, String code, Instant expiresAt, boolean eligible) {
        if (!eligible || !clock.instant().isBefore(expiresAt)) return;
        try {
            sender.send(email, code, expiresAt, VerificationPurpose.PASSWORD_RESET);
        } catch (RuntimeException exception) {
            // 오류의 종류만 기록하여 메일 주소나 인증번호가 예외 메시지로 노출되는 것을 방지합니다.
            log.warn("Password reset mail delivery failed: type={}", exception.getClass().getSimpleName());
        }
    }
}
