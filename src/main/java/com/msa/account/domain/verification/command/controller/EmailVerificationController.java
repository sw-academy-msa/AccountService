package com.msa.account.domain.verification.command.controller;

import com.msa.account.domain.verification.command.dto.request.EmailVerificationRequest;
import com.msa.account.domain.verification.command.dto.request.EmailVerificationConfirmRequest;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationResponse;
import com.msa.account.domain.verification.command.dto.response.EmailVerificationProofResponse;
import com.msa.account.domain.verification.command.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 로그인 전 이용하는 가입용 이메일 인증 API입니다. */
@RestController
@RequestMapping("/api/v1/auth/email-verifications")
@SecurityRequirements
@Tag(name = "Email verification", description = "회원가입 전 이메일 소유 확인")
public class EmailVerificationController {
    private final EmailVerificationService service;
    public EmailVerificationController(EmailVerificationService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "가입 인증번호 발송", description = "인증번호는 메일로만 전송됩니다.")
    public EmailVerificationResponse request(@Valid @RequestBody EmailVerificationRequest request) {
        return service.request(request);
    }

    @PostMapping("/confirm")
    @Operation(summary = "인증번호 확인", description = "성공 시 가입 전용 일회용 verificationToken을 반환합니다.")
    public EmailVerificationProofResponse confirm(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        return service.confirm(request);
    }
}
