package com.msa.account.domain.verification.repository;

import com.msa.account.domain.verification.entity.PasswordResetVerification;
import org.springframework.data.jpa.repository.JpaRepository;

/** 비밀번호 재설정 목적의 코드·일회용 증명만 읽고 씁니다. */
public interface PasswordResetVerificationRepository extends JpaRepository<PasswordResetVerification, String> { }
