package com.msa.account.domain.auth.command.dto.request;

/** form의 grant_type/scope를 업무 서비스로 전달합니다. 주체/역할은 클라이언트 입력받지 않습니다. */
public record ServiceTokenRequest(String grantType, String scope) {}
