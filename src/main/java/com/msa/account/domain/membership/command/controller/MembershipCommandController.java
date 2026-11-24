package com.msa.account.domain.membership.command.controller;

import com.msa.account.domain.membership.command.service.MembershipCommandService;
import com.msa.account.domain.membership.dto.response.MembershipCancellationResponse;
import com.msa.account.global.response.*;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 해지 명령 접수까지만 보장합니다. 금융 반영 뒤 기존 refresh로 토큰을 다시 받습니다. */
@RestController
@RequestMapping("/api/v1/accounts/me/membership")
@PreAuthorize("hasRole('BUYER') and principal.claims['principal_type'] == 'user'")
public class MembershipCommandController {
    private final MembershipCommandService service;

    public MembershipCommandController(MembershipCommandService service) {
        this.service = service;
    }

    @PostMapping("/cancellation")
    @Operation(summary = "본인 멤버십 해지 요청")
    public ResponseEntity<ApiResponse<MembershipCancellationResponse>> cancel(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.onSuccess(
                                service.cancel(UUID.fromString(jwt.getSubject())), SuccessCode.OK));
    }
}
