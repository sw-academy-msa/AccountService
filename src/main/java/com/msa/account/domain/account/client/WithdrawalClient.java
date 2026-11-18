package com.msa.account.domain.account.client;

import tools.jackson.databind.JsonNode;

/** Eureka와 기존 RSA 서비스 토큰으로 내부 API만 호출합니다. 외부 Gateway를 왕복하지 않습니다. */
public interface WithdrawalClient {
    JsonNode exchange(String service, String method, String path, Object body);

    boolean enabled();
}
