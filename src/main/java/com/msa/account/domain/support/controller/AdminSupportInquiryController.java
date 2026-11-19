package com.msa.account.domain.support.controller;

import com.msa.account.domain.support.dto.request.SupportRequests;
import com.msa.account.domain.support.dto.response.SupportResponses;
import com.msa.account.domain.support.entity.SupportInquiry;
import com.msa.account.domain.support.service.SupportInquiryService;
import com.msa.account.global.response.ApiResponse;
import com.msa.account.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 운영자의 1:1 문의 목록과 답변입니다. 답변은 한 번만 등록합니다. */
@RestController
@RequestMapping("/api/v1/admin/support-inquiries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportInquiryController {
    private final SupportInquiryService service;

    public AdminSupportInquiryController(SupportInquiryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "1:1 문의 목록", description = "status=WAITING이면 답변 대기만 오래된 순으로, 없으면 전체 최근 순")
    public ApiResponse<SupportResponses.Page> list(
            @RequestParam(required = false) SupportInquiry.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.onSuccess(service.all(status, page, size), SuccessCode.OK);
    }

    @PutMapping("/{id}/answer")
    @Operation(summary = "1:1 문의 답변")
    public ApiResponse<SupportResponses.Inquiry> answer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody SupportRequests.Answer request) {
        return ApiResponse.onSuccess(
                service.answer(UUID.fromString(jwt.getSubject()), id, request), SuccessCode.OK);
    }
}
