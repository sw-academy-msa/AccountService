package com.msa.account.domain.verification.repository;

import com.msa.account.domain.verification.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

/** 이메일 인증 상태를 Account 소유 DB에 영속 저장합니다. */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> { }
