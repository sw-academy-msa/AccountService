package com.msa.account.domain.verification.repository;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 아직 인증 행이 없는 최초 요청까지 이메일별로 직렬화합니다.
 * PostgreSQL 트랜잭션 잠금은 여러 Account 복제 인스턴스 사이에서도 동작합니다.
 */
@Repository
public class EmailVerificationLock {
    private final JdbcTemplate jdbcTemplate;

    public EmailVerificationLock(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    // 반드시 호출자의 DB 트랜잭션 안에서 사용합니다. 종료 시 PostgreSQL이 잠금을 해제합니다.
    public void acquire(String email) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "account:signup-email:" + email);
                statement.execute();
            }
            return null;
        });
    }
}
