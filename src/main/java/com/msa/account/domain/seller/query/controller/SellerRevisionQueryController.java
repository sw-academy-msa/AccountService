package com.msa.account.domain.seller.query.controller;

import com.msa.account.domain.seller.command.service.SellerApplicationRevisionService;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.global.response.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 본인 보완 사유와 판매 제한 상태를 반환하며 타인 신청 존재 여부는 공개하지 않습니다. */
@RestController
@PreAuthorize("hasRole('BUYER')")
public class SellerRevisionQueryController {
    private final SellerApplicationRevisionService service;

    public SellerRevisionQueryController(SellerApplicationRevisionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/seller-applications/{id}/correction-request")
    public ApiResponse<SellerCorrectionResponse> correction(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        SellerCorrectionResponse response =
                service.correction(UUID.fromString(jwt.getSubject()), id, false);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/api/v1/sellers/me/review-status")
    public ApiResponse<SellerReviewStateResponse> state(@AuthenticationPrincipal Jwt jwt) {
        SellerReviewStateResponse response = service.state(UUID.fromString(jwt.getSubject()));
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
