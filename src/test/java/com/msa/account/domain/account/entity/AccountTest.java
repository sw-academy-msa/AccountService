package com.msa.account.domain.account.entity;

import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.exception.AccountException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring이나 DB 없이 회원 엔티티의 기본 권한과 정규화 규칙을 검증합니다.
 */
class AccountTest {

    @Test
    // 휴대전화 없이 가입하며 기본 상태·권한과 이메일·이름 정규화를 확인합니다.
    void registersAsActiveBuyerAndNormalizesEmailAndName() {
        Account account = Account.registerBuyer(
                " Buyer@Example.COM ",
                "encoded-password",
                " 김서윤 "
        );

        assertThat(account.getEmail()).isEqualTo("buyer@example.com");
        assertThat(account.getName()).isEqualTo("김서윤");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getRoles()).containsExactly(Role.BUYER);
    }

    @Test
    // 이미 가진 구매자 권한을 다시 부여하면 도메인 예외가 발생하는지 확인합니다.
    void rejectsDuplicateRoleGrant() {
        Account account = Account.registerBuyer(
                "buyer@example.com",
                "encoded-password",
                "김서윤"
        );

        assertThatThrownBy(() -> account.grantRole(Role.BUYER, null))
                .isInstanceOf(AccountException.class);
    }
}
