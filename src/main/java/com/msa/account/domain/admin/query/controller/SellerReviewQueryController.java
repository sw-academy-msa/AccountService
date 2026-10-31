package com.msa.account.domain.admin.query.controller;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.query.dto.response.*;
import com.msa.account.domain.seller.query.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** 신청 정보는 읽기 전용입니다. PDF 열람 기록은 별도 Command API가 담당합니다. */
@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@PreAuthorize("hasRole('ADMIN')")
public class SellerReviewQueryController {
    private final SellerApplicationQueryService service;
    public SellerReviewQueryController(SellerApplicationQueryService service) {
        this.service=service;
    }
    @GetMapping @Operation(summary="운영자 판매자 신청 목록",description="status로 PENDING·APPROVED·REJECTED를 선택합니다.")
    public ResponseEntity<SellerApplicationPageResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) SellerApplicationStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(UUID.fromString(jwt.getSubject()),status,page,size));
    }
    @GetMapping("/{applicationId}") @Operation(summary="운영자 판매자 신청 상세")
    public ResponseEntity<SellerApplicationResponse> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detail(UUID.fromString(jwt.getSubject()),applicationId));
    }
}
