package com.msa.account.domain.auth.entity;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

/** 이메일별 로그인 실패 횟수를 저장합니다. 미가입 이메일에도 동일한 제한을 적용합니다. */
@Entity
@Table(schema = "account", name = "login_attempts")
public class LoginAttempt {
    @Id @Column(length = 320) private String email;
    @Column(name = "window_started_at", nullable = false) private Instant windowStartedAt;
    @Column(nullable = false) private int failures;
    protected LoginAttempt() { }
    public LoginAttempt(String email, Instant now) { this.email = email; this.windowStartedAt = now; }

    // 초기 실패부터 계산한 시간 창이 끝나면 실패 횟수를 초기화합니다.
    public boolean isBlocked(Instant now, Duration window, int limit) {
        if (!now.isBefore(windowStartedAt.plus(window))) { windowStartedAt = now; failures = 0; }
        return failures >= limit;
    }
    public void recordFailure() { failures++; }
}
