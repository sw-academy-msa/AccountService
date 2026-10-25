package com.msa.account.domain.seller.command.controller;
import com.msa.account.domain.seller.command.dto.request.SellerApplicationRequest;
import com.msa.account.domain.seller.command.dto.response.SellerApplicationCreatedResponse;
import com.msa.account.domain.seller.command.service.SellerApplicationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.*;
/** application(JSON)과 certificate(PDF)를 하나의 multipart 요청으로 접수합니다. */
@RestController
@RequestMapping("/api/v1/seller-applications")
public class SellerApplicationCommandController {
    private final SellerApplicationCommandService service;
    public SellerApplicationCommandController(SellerApplicationCommandService service) { this.service=service; }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="판매자 입점 신청",description="심사 중 수정은 제공하지 않습니다. 거절된 회원만 새 회차로 재신청할 수 있습니다.")
    public ResponseEntity<SellerApplicationCreatedResponse> submit(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("application") SellerApplicationRequest request,
            @RequestPart("certificate") List<MultipartFile> certificates) {
        var result=service.submit(UUID.fromString(jwt.getSubject()),request,certificates);
        return ResponseEntity.created(URI.create("/api/v1/seller-applications/"+result.applicationId()))
                .cacheControl(CacheControl.noStore()).body(result);
    }
}
