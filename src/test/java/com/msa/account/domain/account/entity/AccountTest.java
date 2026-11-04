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
    // 빌더로 생성해도 기존 생성자의 정규화와 기본 권한 규칙을 통과해야 합니다.
    void builderPreservesBuyerDefaultsAndSignupSettings() {
        var account = Account.builder()
                .email(" Buyer@Example.COM ")
                .passwordHash("synthetic-password-hash")
                .name(" 테스트 구매자 ")
                .build();
        var now = java.time.Instant.parse("2030-01-01T00:00:00Z");
        account.initializeSignup(now,
                java.util.Set.of(com.msa.account.domain.account.enums.InterestCategory.FASHION),
                true, false, false, now);

        assertThat(account.getEmail()).isEqualTo("buyer@example.com");
        assertThat(account.getName()).isEqualTo("테스트 구매자");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getRoles()).containsExactly(Role.BUYER);
        assertThat(account.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(account.getNotificationSettings().isSaleStart()).isTrue();
        assertThat(account.getNotificationSettings().isPersonalizedRecommendation()).isFalse();
        assertThat(account.getConsent().isMarketingAgreed()).isFalse();
        assertThat(account.getConsent().getWithdrawnAt()).isNull();
    }

    @Test
    // 자동 생성 빌더의 toString이 이메일·비밀번호 해시를 출력하지 않도록 보호합니다.
    void builderDoesNotExposeCredentials() {
        var builder = Account.builder()
                .email("synthetic@example.test")
                .passwordHash("synthetic-sensitive-hash")
                .name("테스트 사용자");

        assertThat(builder.toString()).doesNotContain(
                "synthetic@example.test", "synthetic-sensitive-hash", "테스트 사용자");
    }

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
