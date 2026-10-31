package com.msa.account.domain.seller.command.controller;

import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.command.service.SellerProfileCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** 판매자 마이페이지의 변경 API입니다. JWT와 DB의 SELLER 자격을 모두 확인합니다. */
@RestController
@RequestMapping("/api/v1/sellers/me")
@PreAuthorize("hasRole('SELLER')")
public class SellerProfileCommandController {
    private final SellerProfileCommandService service;
    public SellerProfileCommandController(SellerProfileCommandService service) { this.service = service; }

    @PutMapping("/contact")
    @Operation(summary = "내 판매자 표시 이름·담당자 정보 수정", description = "회원 기본 정보와 기존 신청서는 변경하지 않습니다.")
    public ResponseEntity<Void> contact(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateSellerContactRequest request) {
        service.updateContact(UUID.fromString(jwt.getSubject()), request); return done();
    }
    @PutMapping("/bank-account")
    @Operation(summary = "내 판매자 정산 계좌 수정", description = "암호화 저장만 수행하며 실제 계좌 확인·정산·송금은 하지 않습니다.")
    public ResponseEntity<Void> bank(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateSellerBankAccountRequest request) {
        service.updateBankAccount(UUID.fromString(jwt.getSubject()), request); return done();
    }
    @PutMapping("/addresses")
    @Operation(summary = "내 판매자 출고·반품 주소 수정")
    public ResponseEntity<Void> addresses(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateSellerAddressesRequest request) {
        service.updateAddresses(UUID.fromString(jwt.getSubject()), request); return done();
    }
    private ResponseEntity<Void> done() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
}
