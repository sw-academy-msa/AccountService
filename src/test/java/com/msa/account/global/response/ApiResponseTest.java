package com.msa.account.global.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP 호출자가 의존할 성공 응답의 JSON 구조와 민감정보 출력 제한을 검증합니다. */
class ApiResponseTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    // 기존 204 대신 반환할 200 응답도 data 키를 명시적으로 null로 제공합니다.
    void emptySuccessKeepsDataField() {
        var response = ApiResponse.onSuccess(null, SuccessCode.OK);
        var body = json.readTree(json.writeValueAsString(response));

        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("code").asText()).isEqualTo("COMMON_SUCCESS");
        assertThat(body.has("data")).isTrue();
        assertThat(body.path("data").isNull()).isTrue();
    }

    @Test
    // 생성 성공은 HTTP 201과 함께 컨트롤러가 CREATED 본문 코드를 사용합니다.
    void createdResponseWrapsPayloadWithoutLoggingIt() {
        var response = ApiResponse.onSuccess(
                java.util.Map.of("accessToken", "synthetic-sensitive-token"), SuccessCode.CREATED);
        var body = json.readTree(json.writeValueAsString(response));

        assertThat(body.path("code").asText()).isEqualTo("COMMON_CREATED");
        assertThat(body.path("data").path("accessToken").asText()).isEqualTo("synthetic-sensitive-token");
        assertThat(response.toString()).doesNotContain("synthetic-sensitive-token");
    }
}
