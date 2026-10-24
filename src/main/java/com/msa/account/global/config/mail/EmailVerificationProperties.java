package com.msa.account.global.config.mail;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

/** 인증번호와 가입 증명의 유효 시간 및 재시도 제한을 환경 설정에서 읽습니다. */
@Validated
@ConfigurationProperties(prefix = "account.email-verification")
public record EmailVerificationProperties(
        @NotNull Duration codeTtl,
        @NotNull Duration proofTtl,
        @NotNull Duration resendInterval,
        @Min(1) int maxAttempts,
        @Min(1) int maxRequestsPerHour,
        @NotBlank String from
) {
    // 잘못된 시간 설정으로 인증 제한이 무효화되는 것을 시작 단계에서 방지합니다.
    public EmailVerificationProperties {
        if (codeTtl == null || proofTtl == null || resendInterval == null
                || codeTtl.isNegative() || codeTtl.isZero()
                || proofTtl.isNegative() || proofTtl.isZero()
                || resendInterval.isNegative() || resendInterval.isZero()) {
            throw new IllegalArgumentException("인증 시간 설정은 양수여야 합니다.");
        }
    }
}
