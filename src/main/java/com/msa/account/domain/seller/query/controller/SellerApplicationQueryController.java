package com.msa.account.domain.seller.query.controller;
import com.msa.account.domain.seller.query.dto.response.SellerApplicationResponse;
import com.msa.account.domain.seller.query.service.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** 본인 신청·거절 사유·이전 회차 서류 조회만 허용합니다. */
@RestController
@RequestMapping("/api/v1/seller-applications")
public class SellerApplicationQueryController {
    private final SellerApplicationQueryService service; private final SellerCertificateQueryService certificates;
    public SellerApplicationQueryController(SellerApplicationQueryService service,SellerCertificateQueryService certificates) {
        this.service=service; this.certificates=certificates;
    }
    @GetMapping("/me") @Operation(summary="내 최신 판매자 신청 조회")
    public ResponseEntity<SellerApplicationResponse> latest(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.latest(UUID.fromString(jwt.getSubject())));
    }
    @GetMapping("/me/history") @Operation(summary="내 판매자 신청 이력 조회")
    public ResponseEntity<List<SellerApplicationResponse>> history(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(UUID.fromString(jwt.getSubject())));
    }
    @GetMapping("/{applicationId}") @Operation(summary="내 판매자 신청 단건 조회")
    public ResponseEntity<SellerApplicationResponse> own(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.own(UUID.fromString(jwt.getSubject()),applicationId));
    }
    @GetMapping(value="/{applicationId}/certificate",produces=MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary="내 사업자등록증 PDF 조회")
    public ResponseEntity<byte[]> certificate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID applicationId) {
        var file=certificates.own(UUID.fromString(jwt.getSubject()),applicationId);
        // 인증 헤더로만 제공하며 공개 URL과 저장 키는 반환하지 않습니다.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"business-registration.pdf\"")
                .header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
}
