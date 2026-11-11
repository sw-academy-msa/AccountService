package com.msa.account.global.config.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

/** 기존 서비스 토큰 발급 계약을 재사용하면서 허용 scope를 최소 범위로 확장합니다. */
class ServiceAuthPersonalizationScopeTest {
    ServiceAuthProperties properties(Set<String> scope) {
        return new ServiceAuthProperties(
                true,
                Duration.ofMinutes(5),
                Map.of(
                        "notification-service",
                        new ServiceAuthProperties.Client(
                                UUID.randomUUID(), "$2a$10$" + "a".repeat(53), scope)));
    }

    @Test
    void permitsNotificationAnalyticsReadScope() {
        assertThatCode(() -> properties(Set.of("ai.personalization.read")))
                .doesNotThrowAnyException();
    }

    @Test
    void preservesExistingOrderScope() {
        assertThatCode(() -> properties(Set.of("product.stock"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnrecognizedScopes() {
        assertThatThrownBy(() -> properties(Set.of("ai.admin")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
