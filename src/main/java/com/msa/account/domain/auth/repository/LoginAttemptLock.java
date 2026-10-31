package com.msa.account.domain.auth.repository;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 아직 실패 기록이 없는 경우도 포함해 복제 인스턴스의 로그인 횟수 계산을 직렬화합니다. */
@Repository
public class LoginAttemptLock {
    private final JdbcTemplate jdbc;
    public LoginAttemptLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // 서비스의 로그인 트랜잭션이 종료되면 PostgreSQL이 잠금을 해제합니다.
    public void acquire(String email) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "account:login:" + email);
                statement.execute();
            }
            return null;
        });
    }
}
