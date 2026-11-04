package com.msa.account.domain.auth.command.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 인증 응답을 조립하는 중에도 토큰이 문자열 출력으로 유출되지 않는지 검사합니다. */
class TokenResponseTest {
    @Test
    // 사용자·서비스 두 응답의 빌더와 완성된 응답 모두 원문을 감춰야 합니다.
    void buildersAndResponsesDoNotExposeTokens() {
        String token = "synthetic-sensitive-access-token";
        var user = TokenResponse.builder().accessToken(token).tokenType("Bearer");
        var service = ServiceTokenResponse.builder().accessToken(token).tokenType("Bearer");

        assertThat(user.toString()).doesNotContain(token);
        assertThat(user.build().toString()).doesNotContain(token);
        assertThat(service.toString()).doesNotContain(token);
        assertThat(service.build().toString()).doesNotContain(token);
    }
}
