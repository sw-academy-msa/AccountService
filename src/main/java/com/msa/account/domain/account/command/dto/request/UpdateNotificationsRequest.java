package com.msa.account.domain.account.command.dto.request;
import jakarta.validation.constraints.NotNull;
/** 두 유형의 수신 설정을 명시적으로 전달하며 광고 동의를 함께 변경하지 않습니다. */
public record UpdateNotificationsRequest(@NotNull Boolean saleStart,
        @NotNull Boolean personalizedRecommendation) { }
