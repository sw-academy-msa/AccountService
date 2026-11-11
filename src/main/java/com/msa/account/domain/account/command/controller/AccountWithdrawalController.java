package com.msa.account.domain.account.command.controller;

import com.msa.account.domain.account.command.dto.request.WithdrawAccountRequest;
import com.msa.account.domain.account.command.dto.response.WithdrawalResponse;
import com.msa.account.domain.account.command.service.AccountWithdrawalService;
import com.msa.account.domain.auth.command.controller.RefreshTokenCookie;
import com.msa.account.global.response.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Bearer와 현재 비밀번호를 확인합니다. 성공 때만 Refresh 쿠키를 제거합니다. */
@RestController
@RequestMapping("/api/v1/accounts/me")
public class AccountWithdrawalController {
    private final AccountWithdrawalService service;
    private final RefreshTokenCookie cookie;

    public AccountWithdrawalController(
            AccountWithdrawalService service, RefreshTokenCookie cookie) {
        this.service = service;
        this.cookie = cookie;
    }

    @DeleteMapping
    public ApiResponse<WithdrawalResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WithdrawAccountRequest request,
            HttpServletResponse http) {
        WithdrawalResponse response = service.withdraw(UUID.fromString(jwt.getSubject()), request);
        cookie.clear(http);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
