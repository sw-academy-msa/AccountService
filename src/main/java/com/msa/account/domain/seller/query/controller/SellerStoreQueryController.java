package com.msa.account.domain.seller.query.controller;

import com.msa.account.domain.seller.command.service.SellerStoreService;
import com.msa.account.domain.seller.query.dto.response.StoreResponse;
import com.msa.account.global.response.*;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 본인 설정과 로고 조회입니다. 공개 스토어 조회 모델은 이 컨트롤러가 소유하지 않습니다. */
@RestController
@RequestMapping("/api/v1/sellers/me/store")
@PreAuthorize("hasRole('SELLER')")
public class SellerStoreQueryController {
    private final SellerStoreService service;

    public SellerStoreQueryController(SellerStoreService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<StoreResponse> get(@AuthenticationPrincipal Jwt jwt) {
        StoreResponse response = service.get(UUID.fromString(jwt.getSubject()));
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(@AuthenticationPrincipal Jwt jwt) {
        var content = service.logo(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }
}
