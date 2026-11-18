package com.msa.account.global.config.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 서비스 토큰 발급은 운영에서 TLS만 허용하고, 사설망 전용 배포는 명시 설정으로만 평문을 허용하는지 확인합니다. */
class ServiceTokenTransportTest {
    @Test
    void productionRequiresTlsUnlessPlainHttpIsExplicitlyAllowed() {
        var prod = profile("prod");
        assertThat(ServiceTokenSecurityConfig.transportAllowed(prod, true)).isTrue();
        assertThat(ServiceTokenSecurityConfig.transportAllowed(prod, false)).isFalse();

        prod.setProperty("account.service-auth.allow-plain-http", "true");
        assertThat(ServiceTokenSecurityConfig.transportAllowed(prod, false)).isTrue();
    }

    @Test
    void localProfileAllowsPlainHttp() {
        assertThat(ServiceTokenSecurityConfig.transportAllowed(profile("local"), false)).isTrue();
    }

    private static MockEnvironment profile(String name) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(name);
        return environment;
    }
}
