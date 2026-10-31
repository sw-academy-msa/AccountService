package com.msa.account.domain.seller.query.controller;

import com.msa.account.domain.seller.query.dto.response.SellerProfileResponse;
import com.msa.account.domain.seller.query.service.SellerProfileQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** 현재 판매자 정보를 조회합니다. PDF는 응답의 approvedApplicationId로 신청별 조회 API를 사용합니다. */
@RestController
@RequestMapping("/api/v1/sellers/me")
@PreAuthorize("hasRole('SELLER')")
public class SellerProfileQueryController {
    private final SellerProfileQueryService service;
    public SellerProfileQueryController(SellerProfileQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "내 판매자 마이페이지 정보 조회")
    public ResponseEntity<SellerProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(UUID.fromString(jwt.getSubject())));
    }
}
