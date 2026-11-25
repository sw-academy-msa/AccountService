package com.msa.account.domain.membership.query.controller;

import com.msa.account.domain.membership.dto.response.MembershipResponse;
import com.msa.account.domain.membership.query.service.MembershipQueryService;
import com.msa.account.global.response.*;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 검증한 사용자 sub의 자격만 조회하며 응답 캐시를 금지합니다. */
@RestController
@RequestMapping("/api/v1/accounts/me/membership")
@PreAuthorize("hasRole('BUYER') and principal.claims['principal_type'] == 'user'")
public class MembershipQueryController {
    private final MembershipQueryService service;

    public MembershipQueryController(MembershipQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "본인 멤버십 자격 조회")
    public ResponseEntity<ApiResponse<MembershipResponse>> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.onSuccess(
                                service.get(UUID.fromString(jwt.getSubject())), SuccessCode.OK));
    }
}
