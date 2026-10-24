package com.msa.account.domain.account.query.controller;
import com.msa.account.domain.account.query.service.AccountSettingsQueryService;
import com.msa.account.domain.account.query.dto.response.AccountSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** 본인 프로필·관심·알림·동의를 한 번에 조회하는 API입니다. */
@RestController
@RequestMapping("/api/v1/accounts/me")
public class AccountSettingsQueryController {
    private final AccountSettingsQueryService service;
    public AccountSettingsQueryController(AccountSettingsQueryService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "내 정보와 설정 조회")
    public ResponseEntity<AccountSettingsResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getSettings(UUID.fromString(jwt.getSubject())));
    }
}
