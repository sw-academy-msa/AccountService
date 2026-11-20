package com.msa.account.domain.auth.query.service;

/** 관리자 요청마다 토큰 주체의 현재 계정 자격을 확인합니다. */
public interface AdminAccessService {
    boolean isCurrentAdmin(String subject);
}
