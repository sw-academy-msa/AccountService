package com.msa.account.domain.seller.command.controller;

import com.msa.account.domain.seller.command.dto.request.SellerAddressEntryRequest;
import com.msa.account.domain.seller.command.service.SellerAddressBookService;
import com.msa.account.domain.seller.enums.SellerAddressType;
import com.msa.account.domain.seller.query.dto.response.SellerAddressEntryResponse;
import com.msa.account.global.response.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 기존 /addresses 전체 변경과 구분되는 주소록 경로입니다. 타인 계정 ID는 받지 않습니다. */
@RestController
@RequestMapping("/api/v1/sellers/me/address-book/{type}")
@PreAuthorize("hasRole('SELLER')")
public class SellerAddressBookCommandController {
    private final SellerAddressBookService service;

    public SellerAddressBookCommandController(SellerAddressBookService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<SellerAddressEntryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable SellerAddressType type,
            @Valid @RequestBody SellerAddressEntryRequest request) {
        SellerAddressEntryResponse response =
                service.create(UUID.fromString(jwt.getSubject()), type, request);
        return ApiResponse.onSuccess(response, SuccessCode.CREATED);
    }

    @PutMapping("/{id}")
    public ApiResponse<SellerAddressEntryResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable SellerAddressType type,
            @PathVariable UUID id,
            @Valid @RequestBody SellerAddressEntryRequest request) {
        SellerAddressEntryResponse response =
                service.update(UUID.fromString(jwt.getSubject()), type, id, request);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/{id}/default")
    public ApiResponse<Void> select(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable SellerAddressType type,
            @PathVariable UUID id) {
        service.selectDefault(UUID.fromString(jwt.getSubject()), type, id);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable SellerAddressType type,
            @PathVariable UUID id) {
        service.delete(UUID.fromString(jwt.getSubject()), type, id);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }
}
