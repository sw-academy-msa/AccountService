package com.msa.account.domain.auth.command.service;

/** 보존 기간이 지난 세션과 Refresh Token 해시를 한 배치씩 제거하는 계약입니다. */
public interface AuthCleanupService {
    int deleteExpiredBatch();
}
