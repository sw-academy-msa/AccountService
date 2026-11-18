package com.msa.account.domain.account.command.dto.response;

import com.msa.account.domain.account.entity.Account;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 가입 응답의 문자열 출력으로 개인정보가 로그에 유출되는 것을 방지합니다. */
class SignupResponseTest {
    @Test
    // 엔티티 변환 이후에도 문자열은 가리되 실제 응답 필드는 유지해야 합니다.
    void hidesPersonalInformationInResponseAndBuilder() {
        var account = Account.registerBuyer("synthetic@example.test", "synthetic-hash", "테스트 사용자");
        var response = SignupResponse.from(account);

        assertThat(response.toString()).isEqualTo("SignupResponse[REDACTED]");
        assertThat(SignupResponse.builder().email(account.getEmail()).name(account.getName()).toString())
                .isEqualTo("SignupResponseBuilder[REDACTED]");
        assertThat(response.email()).isEqualTo(account.getEmail());
        assertThat(response.name()).isEqualTo(account.getName());
    }
}
