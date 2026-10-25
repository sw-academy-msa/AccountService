package com.msa.account.domain.admin.command.controller;
import com.msa.account.domain.seller.command.dto.request.RejectSellerApplicationRequest;
import com.msa.account.domain.seller.command.service.SellerReviewCommandService;
import com.msa.account.domain.seller.command.service.SellerCertificateReviewCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** 운영자 JWT 권한과 서비스 내부의 최신 DB 권한을 모두 검증합니다. */
@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@PreAuthorize("hasRole('ADMIN')")
public class SellerReviewCommandController {
    private final SellerReviewCommandService service;
    private final SellerCertificateReviewCommandService certificates;
    public SellerReviewCommandController(SellerReviewCommandService service,SellerCertificateReviewCommandService certificates) {
        this.service=service; this.certificates=certificates;
    }
    @PostMapping(value="/{applicationId}/certificate-review",produces=MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary="운영자 PDF 열람 및 기록",description="PDF 응답과 함께 승인에 필요한 열람 이력을 생성하므로 Command로 처리합니다.")
    public ResponseEntity<byte[]> certificate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID applicationId) {
        var file=certificates.review(UUID.fromString(jwt.getSubject()),applicationId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"business-registration.pdf\"")
                .header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
    @PostMapping("/{applicationId}/approve") @Operation(summary="판매자 신청 승인",description="현재 운영자가 PDF를 먼저 조회해야 합니다. BUYER를 유지하고 SELLER를 추가합니다.")
    public ResponseEntity<Void> approve(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID applicationId) {
        service.approve(UUID.fromString(jwt.getSubject()),applicationId); return done();
    }
    @PostMapping("/{applicationId}/reject") @Operation(summary="판매자 신청 거절")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID applicationId,
            @Valid @RequestBody RejectSellerApplicationRequest request) {
        service.reject(UUID.fromString(jwt.getSubject()),applicationId,request.reason()); return done();
    }
    private ResponseEntity<Void> done() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
}
