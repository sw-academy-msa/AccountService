package com.msa.account.domain.auth.command.service;

import com.msa.account.domain.auth.command.dto.request.LoginRequest;
import com.msa.account.domain.auth.command.dto.response.AuthTokens;

/** 로그인, 일회용 Refresh Token 교체, 현재 로그인 세션 종료 기능입니다. */
public interface AuthCommandService {
    AuthTokens login(LoginRequest request);
    AuthTokens refresh(String refreshToken);
    void logout(String refreshToken);
}
