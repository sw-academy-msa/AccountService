package com.msa.account.global.config.membership;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 해지 API와 결과 소비 및 탈퇴 연계를 서로 독립적으로 켭니다. 기본은 모두 꺼져 있습니다. */
@ConfigurationProperties(prefix = "account.membership")
public record MembershipProperties(
        @DefaultValue("false") boolean apiEnabled,
        @DefaultValue("false") boolean consumerEnabled,
        @DefaultValue("false") boolean withdrawalCheckEnabled) {
    @Override
    public String toString() {
        return "MembershipProperties[REDACTED]";
    }
}
