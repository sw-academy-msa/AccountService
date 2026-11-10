package com.msa.account.domain.seller.command.controller;

import com.msa.account.domain.seller.command.dto.request.UpdateStoreRequest;
import com.msa.account.domain.seller.command.service.SellerStoreService;
import com.msa.account.domain.seller.query.dto.response.StoreResponse;
import com.msa.account.global.response.*;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** 판매자 ID는 요청에서 받지 않고 검증한 JWT에서 결정합니다. */
@RestController
@RequestMapping("/api/v1/sellers/me/store")
@PreAuthorize("hasRole('SELLER')")
public class SellerStoreCommandController {
    private final SellerStoreService service;

    public SellerStoreCommandController(SellerStoreService service) {
        this.service = service;
    }

    @PutMapping
    public ApiResponse<StoreResponse> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateStoreRequest request) {
        StoreResponse response = service.update(UUID.fromString(jwt.getSubject()), request);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StoreResponse> upload(
            @AuthenticationPrincipal Jwt jwt, @RequestPart("image") MultipartFile image) {
        StoreResponse response = service.upload(UUID.fromString(jwt.getSubject()), image);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @DeleteMapping("/logo")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        service.deleteLogo(UUID.fromString(jwt.getSubject()));
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }
}
