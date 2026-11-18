package com.msa.account.global.config.scheduling;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

/** 세션 기록 보존과 한 번의 작업에서 처리할 양을 제한하는 설정입니다. */
@Validated
@ConfigurationProperties("account.auth.cleanup")
public record AuthCleanupProperties(@NotNull Duration retention,
        @Min(1) @Max(1000) int batchSize, @Min(1) @Max(1000) int maxBatches) {
    // 잘못된 기간 설정으로 유효한 기록을 조기에 삭제하지 못하게 합니다.
    public AuthCleanupProperties {
        if (retention == null || retention.isZero() || retention.isNegative())
            throw new IllegalArgumentException("인증 기록 보존 기간은 양수여야 합니다.");
    }
}
