package com.msa.account.domain.auth.command.controller;

import com.msa.account.domain.auth.command.dto.request.ServiceTokenRequest;
import com.msa.account.domain.auth.command.dto.response.*;
import com.msa.account.domain.auth.command.service.ServiceClientService;
import com.msa.account.global.error.*;

import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** 외부 Gateway에 라우팅하지 않는 내부 발급입니다. 자격증명은 Basic에만 받고 form 중복 파라미터를 거절합니다. */
@Hidden
@RestController
public class ServiceTokenController {
    private final ServiceClientService service;

    public ServiceTokenController(ServiceClientService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/internal/v1/auth/service-tokens",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ServiceTokenResponse> issue(
            @AuthenticationPrincipal ServiceClientPrincipal principal,
            @RequestParam MultiValueMap<String, String> form) {
        if (!Set.of("grant_type", "scope").containsAll(form.keySet())
                || form.values().stream().anyMatch(v -> v.size() != 1)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        var response =
                service.issue(
                        principal,
                        new ServiceTokenRequest(
                                form.getFirst("grant_type"), form.getFirst("scope")));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);
    }
}
