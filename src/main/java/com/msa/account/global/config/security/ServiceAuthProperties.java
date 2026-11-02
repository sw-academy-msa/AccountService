package com.msa.account.global.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.*;

/** 허용된 서비스와 BCrypt 해시만 관리합니다. 사용자 테이블·사용자 로그인 세션을 서비스 계정으로 복제하지 않습니다. */
@ConfigurationProperties("account.service-auth")
public record ServiceAuthProperties(
        boolean enabled, Duration tokenTtl, Map<String, Client> clients) {
    public record Client(UUID subject, String secretHash, Set<String> scopes) {
        public Client {
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }

        @Override
        public String toString() {
            return "ServiceClient[REDACTED]";
        }
    }

    public ServiceAuthProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
        if (enabled) {
            if (tokenTtl == null
                    || tokenTtl.toSeconds() < 60
                    || tokenTtl.compareTo(Duration.ofMinutes(15)) > 0
                    || clients.isEmpty()) {
                throw new IllegalArgumentException("서비스 토큰 유효 시간과 허용 클라이언트가 필요합니다.");
            }
            var subjects = new HashSet<UUID>();
            clients.forEach(
                    (id, c) -> {
                        if (!id.matches("[a-z][a-z0-9-]{2,99}")
                                || c.subject() == null
                                || !subjects.add(c.subject())
                                || c.secretHash() == null
                                || !c.secretHash()
                                        .matches("\\$2[aby]\\$(1[0-6])\\$[./A-Za-z0-9]{53}")
                                || c.scopes().isEmpty()
                                || !Set.of("product.stock").containsAll(c.scopes())) {
                            throw new IllegalArgumentException(
                                    "서비스 주체·비밀값 해시·허용 scope 설정이 올바르지 않습니다.");
                        }
                    });
        }
    }

    @Override
    public String toString() {
        return "ServiceAuthProperties[REDACTED]";
    }
}
