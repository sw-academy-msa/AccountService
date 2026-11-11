package com.msa.account.domain.admin.command.controller;

import com.msa.account.domain.seller.command.dto.request.RequestSellerCorrections;
import com.msa.account.domain.seller.command.service.SellerApplicationRevisionService;
import com.msa.account.domain.seller.query.dto.response.SellerCorrectionResponse;
import com.msa.account.global.response.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 운영자가 건별 보완 항목과 기한을 지정합니다. 고정 보완 일수를 임의 적용하지 않습니다. */
@RestController
@RequestMapping("/api/v1/admin/seller-applications/{id}/correction-request")
@PreAuthorize("hasRole('ADMIN')")
public class SellerCorrectionCommandController {
    private final SellerApplicationRevisionService service;

    public SellerCorrectionCommandController(SellerApplicationRevisionService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<SellerCorrectionResponse> request(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RequestSellerCorrections request) {
        SellerCorrectionResponse response =
                service.requestCorrections(UUID.fromString(jwt.getSubject()), id, request);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
