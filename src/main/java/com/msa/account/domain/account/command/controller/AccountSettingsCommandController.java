package com.msa.account.domain.account.command.controller;
import com.msa.account.domain.account.command.dto.request.*;
import com.msa.account.domain.account.command.service.AccountSettingsCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** Bearer JWT로만 본인 변경을 인증합니다. Refresh 쿠키로는 이 API를 호출할 수 없습니다. */
@RestController
@RequestMapping("/api/v1/accounts/me")
public class AccountSettingsCommandController {
    private final AccountSettingsCommandService service;
    public AccountSettingsCommandController(AccountSettingsCommandService service) { this.service = service; }
    @PatchMapping
    @Operation(summary = "내 이름 수정")
    public ResponseEntity<Void> profile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        service.updateProfile(UUID.fromString(jwt.getSubject()), request); return done();
    }
    @PutMapping("/interests")
    @Operation(summary = "관심 카테고리 전체 변경", description = "빈 배열을 보내면 관심 카테고리를 모두 해제합니다.")
    public ResponseEntity<Void> interests(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateInterestsRequest request) {
        service.updateInterests(UUID.fromString(jwt.getSubject()), request); return done();
    }
    @PutMapping("/notification-settings")
    @Operation(summary = "알림 유형 설정 변경")
    public ResponseEntity<Void> notifications(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateNotificationsRequest request) {
        service.updateNotifications(UUID.fromString(jwt.getSubject()), request); return done();
    }
    @PutMapping("/marketing-consent")
    @Operation(summary = "광고성 알림 동의 또는 철회", description = "실제로 상태가 바뀔 때만 이력을 기록합니다.")
    public ResponseEntity<Void> consent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateConsentRequest request) {
        service.updateConsent(UUID.fromString(jwt.getSubject()), request); return done();
    }
    // 개인정보 변경 응답은 캐시에 보관하지 않습니다.
    private ResponseEntity<Void> done() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
}
