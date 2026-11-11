package com.msa.account.domain.account.query.controller;

import com.msa.account.domain.account.query.dto.response.NotificationEligibilityResponse;
import com.msa.account.domain.account.query.service.NotificationEligibilityService;
import com.msa.account.global.response.*;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Gateway에 공개하지 않는 서비스 간 읽기 API입니다. JWT의 서비스 주체는 별도 필터에서 검사합니다. */
@RestController
@RequestMapping("/internal/v1/notification-eligibility")
public class NotificationEligibilityController {
    /** 내부 Notification만 선택할 수 있는 수신 목적이며 공개 사용자 파라미터가 아닙니다. */
    public enum Purpose {
        PERSONALIZED,
        TRANSACTIONAL
    }

    private final NotificationEligibilityService service;

    public NotificationEligibilityController(NotificationEligibilityService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<NotificationEligibilityResponse> check(
            @RequestParam UUID accountId,
            @RequestParam(defaultValue = "PERSONALIZED") Purpose purpose) {
        return ApiResponse.onSuccess(
                (purpose == Purpose.TRANSACTIONAL
                        ? service.checkTransactional(accountId)
                        : service.check(accountId)),
                SuccessCode.OK);
    }
}
