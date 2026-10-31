package com.msa.account.domain.auth.command.controller;
import com.msa.account.domain.auth.command.dto.request.ChangePasswordRequest;
import com.msa.account.domain.auth.command.service.PasswordChangeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** 비밀번호 변경은 Bearer 인증과 현재 비밀번호를 모두 요구합니다. */
@RestController
public class PasswordChangeController {
    private final PasswordChangeService service;
    private final RefreshTokenCookie cookie;
    public PasswordChangeController(PasswordChangeService service, RefreshTokenCookie cookie) {
        this.service = service; this.cookie = cookie;
    }
    @PutMapping("/api/v1/accounts/me/password")
    @Operation(summary = "현재 비밀번호로 비밀번호 변경", description = "모든 Refresh 세션을 폐기하므로 다시 로그인합니다. 기존 Access JWT는 만료까지 유효합니다.")
    public ResponseEntity<Void> change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        service.change(UUID.fromString(jwt.getSubject()), request);
        cookie.clear(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
