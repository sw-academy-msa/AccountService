package com.msa.account.domain.support.controller;

import com.msa.account.domain.support.dto.SupportDtos;
import com.msa.account.domain.support.entity.SupportInquiry;
import com.msa.account.domain.support.service.SupportInquiryService;
import com.msa.account.global.response.ApiResponse;
import com.msa.account.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 로그인한 구매자·판매자의 운영팀 1:1 문의입니다. */
@RestController
@RequestMapping("/api/v1/support-inquiries")
@PreAuthorize("hasRole('BUYER') or hasRole('SELLER')")
public class SupportInquiryController {
    private final SupportInquiryService service;

    public SupportInquiryController(SupportInquiryService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "1:1 문의 작성")
    public ApiResponse<SupportDtos.Inquiry> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SupportDtos.Create request) {
        return ApiResponse.onSuccess(
                service.create(UUID.fromString(jwt.getSubject()), request), SuccessCode.CREATED);
    }

    @GetMapping
    @Operation(summary = "내 1:1 문의 목록")
    public ApiResponse<SupportDtos.Page> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.onSuccess(
                service.mine(UUID.fromString(jwt.getSubject()), page, size), SuccessCode.OK);
    }

    @GetMapping("/{id}")
    @Operation(summary = "내 1:1 문의 상세")
    public ApiResponse<SupportDtos.Inquiry> one(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.onSuccess(service.mineOne(UUID.fromString(jwt.getSubject()), id), SuccessCode.OK);
    }
}
