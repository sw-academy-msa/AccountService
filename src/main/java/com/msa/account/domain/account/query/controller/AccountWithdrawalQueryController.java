package com.msa.account.domain.account.query.controller;

import com.msa.account.domain.account.command.service.AccountWithdrawalService;
import com.msa.account.domain.account.query.dto.response.WithdrawalEligibilityResponse;
import com.msa.account.global.response.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 탈퇴 화면의 진행 거래 확인 결과입니다. 미확인은 탈퇴 가능으로 표시하지 않습니다. */
@RestController
public class AccountWithdrawalQueryController {
    private final AccountWithdrawalService service;

    public AccountWithdrawalQueryController(AccountWithdrawalService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/accounts/me/withdrawal-eligibility")
    public ApiResponse<WithdrawalEligibilityResponse> get(@AuthenticationPrincipal Jwt jwt) {
        WithdrawalEligibilityResponse response =
                service.eligibility(UUID.fromString(jwt.getSubject()));
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
