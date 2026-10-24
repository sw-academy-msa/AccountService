package com.msa.account.domain.address.query.controller;

import com.msa.account.domain.address.query.dto.response.AddressResponse;
import com.msa.account.domain.address.query.service.AddressQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 본인 배송지의 조회 전용 API입니다. 개인 정보이므로 응답을 캐시하지 않습니다. */
@RestController
@RequestMapping("/api/v1/accounts/me/addresses")
public class AddressQueryController {
    private final AddressQueryService service;
    public AddressQueryController(AddressQueryService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "내 배송지 목록 조회", description = "기본 배송지가 먼저 표시됩니다.")
    public ResponseEntity<List<AddressResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(UUID.fromString(jwt.getSubject())));
    }
    @GetMapping("/{addressId}")
    @Operation(summary = "내 배송지 단건 조회")
    public ResponseEntity<AddressResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(UUID.fromString(jwt.getSubject()), addressId));
    }
}
