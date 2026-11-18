package com.msa.account.domain.auth.repository;

import com.msa.account.domain.auth.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

/** 로그인 성공 시 실패 기록을 제거하고 실패 시 횟수를 보관합니다. */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> { }
