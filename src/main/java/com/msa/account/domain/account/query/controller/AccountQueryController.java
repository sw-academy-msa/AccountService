package com.msa.account.domain.account.query.controller;

import com.msa.account.domain.account.query.dto.response.SignupOptionsResponse;
import com.msa.account.domain.account.query.service.AccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;

/** 가입 전에도 조회할 수 있는 카테고리·알림 선택지 API입니다. */
@RestController
@RequestMapping("/api/v1/signup-options")
@SecurityRequirements
public class AccountQueryController {
    private final AccountQueryService service;
    public AccountQueryController(AccountQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "회원가입 선택 항목 조회")
    public SignupOptionsResponse signupOptions() { return service.signupOptions(); }
}
