package com.msa.account.domain.auth.command.controller;

import com.msa.account.domain.auth.command.dto.request.*;
import com.msa.account.domain.auth.command.dto.response.PasswordResetProofResponse;
import com.msa.account.domain.auth.command.service.PasswordResetService;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 로그인 전 사용할 수 있는 재설정 API입니다. 쿠키가 아닌 이메일 증명으로 변경 권한을 확인합니다. */
@RestController
@RequestMapping("/api/v1/auth/password-resets")
@SecurityRequirements
public class PasswordResetController {
    private final PasswordResetService service;
    private final RefreshTokenCookie cookie;
    public PasswordResetController(PasswordResetService service, RefreshTokenCookie cookie) {
        this.service = service; this.cookie = cookie;
    }
    @PostMapping
    @Operation(summary = "비밀번호 재설정 인증번호 요청", description = "가입 여부와 무관하게 같은 응답을 반환하며 유효한 계정에만 메일을 보냅니다.")
    public ResponseEntity<EmailVerificationResponse> request(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.request(request));
    }
    @PostMapping("/confirm")
    @Operation(summary = "비밀번호 재설정 인증번호 확인", description = "가입용 토큰과 교환할 수 없는 일회용 resetToken을 반환합니다.")
    public ResponseEntity<PasswordResetProofResponse> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.confirm(request));
    }
    @PostMapping("/complete")
    @Operation(summary = "새 비밀번호 저장", description = "증명 소비와 모든 Refresh 세션 폐기를 함께 처리합니다. Access JWT의 즉시 회수는 수행하지 않습니다.")
    public ResponseEntity<Void> complete(@Valid @RequestBody PasswordResetCompleteRequest request, HttpServletResponse response) {
        service.complete(request);
        cookie.clear(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
