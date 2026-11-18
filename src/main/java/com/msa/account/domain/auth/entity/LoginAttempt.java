package com.msa.account.domain.auth.entity;

import lombok.Builder;

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
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder
    public LoginAttempt(String email, Instant now) { this.email = email; this.windowStartedAt = now; }

    // 초기 실패부터 계산한 시간 창이 끝나면 실패 횟수를 초기화합니다.
    public boolean isBlocked(Instant now, Duration window, int limit) {
        if (!now.isBefore(windowStartedAt.plus(window))) { windowStartedAt = now; failures = 0; }
        return failures >= limit;
    }
    public void recordFailure() { failures++; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    public static class LoginAttemptBuilder {
        @Override
        public String toString() {
            return "LoginAttemptBuilder[REDACTED]";
        }
    }
}
