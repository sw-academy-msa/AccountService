package com.msa.account.domain.verification.command.service;

import java.time.Instant;

/** 인증 기록이 커밋된 뒤에만 메일 작업을 실행합니다. 미가입 주소도 동일하게 작업을 등록합니다. */
public interface PasswordResetMailDispatcher {
    void dispatchAfterCommit(String email, String code, Instant expiresAt, boolean eligible);
}
