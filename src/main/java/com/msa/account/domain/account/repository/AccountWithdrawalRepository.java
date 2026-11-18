package com.msa.account.domain.account.repository;

import com.msa.account.domain.account.entity.AccountWithdrawal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 탈퇴 이력은 Account DB 안에서만 조회합니다. */
public interface AccountWithdrawalRepository extends JpaRepository<AccountWithdrawal, UUID> {}
