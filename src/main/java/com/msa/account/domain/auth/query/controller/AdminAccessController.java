package com.msa.account.domain.auth.query.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 다른 서비스가 사용자 Bearer로 본인의 현재 관리자 자격만 확인하는 읽기 전용 API입니다. */
@RestController
public class AdminAccessController {
    @GetMapping("/api/v1/auth/admin-access")
    public ResponseEntity<Void> check() {
        // SecurityConfig가 JWT와 현재 DB 자격을 모두 확인한 요청만 도달하며 결과를 캐시하지 않습니다.
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
