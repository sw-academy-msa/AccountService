package com.msa.account.domain.seller.query.controller;

import com.msa.account.domain.seller.command.service.SellerAddressBookService;
import com.msa.account.domain.seller.enums.SellerAddressType;
import com.msa.account.domain.seller.query.dto.response.SellerAddressEntryResponse;
import com.msa.account.global.response.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 본인 주소록 조회입니다. Product와 Order의 다른 DB에는 접근하지 않습니다. */
@RestController
@RequestMapping("/api/v1/sellers/me/address-book/{type}")
@PreAuthorize("hasRole('SELLER')")
public class SellerAddressBookQueryController {
    private final SellerAddressBookService service;

    public SellerAddressBookQueryController(SellerAddressBookService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SellerAddressEntryResponse>> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable SellerAddressType type) {
        List<SellerAddressEntryResponse> response =
                service.list(UUID.fromString(jwt.getSubject()), type);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}")
    public ApiResponse<SellerAddressEntryResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable SellerAddressType type,
            @PathVariable UUID id) {
        SellerAddressEntryResponse response =
                service.get(UUID.fromString(jwt.getSubject()), type, id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
