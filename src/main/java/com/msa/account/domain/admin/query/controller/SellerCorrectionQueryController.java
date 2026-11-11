package com.msa.account.domain.admin.query.controller;

import com.msa.account.domain.seller.command.service.SellerApplicationRevisionService;
import com.msa.account.domain.seller.query.dto.response.SellerCorrectionResponse;
import com.msa.account.global.response.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 운영자는 JWT 역할과 DB의 현재 운영자 권한을 모두 확인합니다. */
@RestController
@RequestMapping("/api/v1/admin/seller-applications/{id}/correction-request")
@PreAuthorize("hasRole('ADMIN')")
public class SellerCorrectionQueryController {
    private final SellerApplicationRevisionService service;

    public SellerCorrectionQueryController(SellerApplicationRevisionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SellerCorrectionResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        SellerCorrectionResponse response =
                service.correction(UUID.fromString(jwt.getSubject()), id, true);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
