package com.msa.account.domain.account.command.dto.request;
import jakarta.validation.constraints.NotNull;
/** 광고성 알림 동의·철회를 처리하며 미입력을 철회로 해석하지 않습니다. */
public record UpdateConsentRequest(@NotNull Boolean marketingConsent) { }
