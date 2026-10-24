package com.msa.account.domain.address.command.controller;

import com.msa.account.domain.address.command.dto.request.AddressRequest;
import com.msa.account.domain.address.command.dto.response.AddressCreatedResponse;
import com.msa.account.domain.address.command.service.AddressCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

/** Bearer 인증 후 서비스에서 현재 구매자 권한과 배송지 소유권을 확인합니다. */
@RestController
@RequestMapping("/api/v1/accounts/me/addresses")
public class AddressCommandController {
    private final AddressCommandService service;
    public AddressCommandController(AddressCommandService service) { this.service = service; }
    @PostMapping
    @Operation(summary = "배송지 등록", description = "카카오 우편번호 검색에서 선택한 주소와 수령 정보를 저장합니다. 첫 배송지는 기본으로 지정합니다.")
    public ResponseEntity<AddressCreatedResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        var result = service.create(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/me/addresses/" + result.addressId()))
                .cacheControl(CacheControl.noStore()).body(result);
    }
    @PutMapping("/{addressId}")
    @Operation(summary = "선택한 배송지 수정", description = "기본 여부는 변경하지 않습니다.")
    public ResponseEntity<Void> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        service.update(UUID.fromString(jwt.getSubject()), addressId, request); return done();
    }
    @DeleteMapping("/{addressId}")
    @Operation(summary = "배송지 삭제", description = "다른 주소가 남으면 기본 배송지를 먼저 변경해야 합니다.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        service.delete(UUID.fromString(jwt.getSubject()), addressId); return done();
    }
    @PutMapping("/{addressId}/default")
    @Operation(summary = "기본 배송지 지정")
    public ResponseEntity<Void> setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        service.setDefault(UUID.fromString(jwt.getSubject()), addressId); return done();
    }
    // 변경된 개인 주소 응답을 중간 캐시에 보관하지 않습니다.
    private ResponseEntity<Void> done() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
}
