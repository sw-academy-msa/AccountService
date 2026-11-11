package com.msa.account.domain.seller.command.controller;

import com.msa.account.domain.seller.command.dto.request.SellerCorrectionSubmissionRequest;
import com.msa.account.domain.seller.command.dto.response.SellerApplicationCreatedResponse;
import com.msa.account.domain.seller.command.service.SellerApplicationRevisionService;
import com.msa.account.global.response.*;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** 판매 제한 중에도 본인 재심사 제출과 보완을 할 수 있도록 BUYER와 원본 소유권으로 검사합니다. */
@RestController
@PreAuthorize("hasRole('BUYER')")
public class SellerRevisionCommandController {
    private final SellerApplicationRevisionService service;

    public SellerRevisionCommandController(SellerApplicationRevisionService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/api/v1/seller-applications/{id}/resubmissions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<SellerApplicationCreatedResponse> resubmit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestPart("application") SellerCorrectionSubmissionRequest request,
            @RequestPart(value = "certificate", required = false) List<MultipartFile> files) {
        SellerApplicationCreatedResponse response =
                service.resubmit(UUID.fromString(jwt.getSubject()), id, request, files);
        return ApiResponse.onSuccess(response, SuccessCode.CREATED);
    }

    @PostMapping(
            value = "/api/v1/sellers/me/certificate-reviews",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<SellerApplicationCreatedResponse> recheck(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("certificate") List<MultipartFile> files) {
        SellerApplicationCreatedResponse response =
                service.recheck(UUID.fromString(jwt.getSubject()), files);
        return ApiResponse.onSuccess(response, SuccessCode.CREATED);
    }
}
