package com.msa.account.domain.account.command.controller;

import com.msa.account.domain.account.command.dto.request.SignupRequest;
import com.msa.account.domain.account.command.dto.response.SignupResponse;
import com.msa.account.domain.account.command.service.AccountCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 정보를 변경하는 HTTP 요청을 받습니다. 입력 검증 후 실제 가입 처리는 서비스에 위임합니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "회원가입과 인증 API")
public class AccountCommandController {

    private final AccountCommandService accountCommandService;

    public AccountCommandController(AccountCommandService accountCommandService) {
        this.accountCommandService = accountCommandService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "이메일 회원가입", description = "이메일 인증 증명을 확인하고 회원·선택 설정을 함께 저장합니다. BUYER 권한을 부여합니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "입력값 검증 실패")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    // 회원가입 요청을 처리하고 가입 결과를 반환합니다.
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return accountCommandService.signup(request);
    }
}
