package com.msa.account.domain.account.entity;

import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.account.exception.AccountErrorCode;
import com.msa.account.domain.account.exception.AccountException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.FetchType;
import com.msa.account.domain.account.enums.InterestCategory;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 회원 정보와 권한을 관리하는 엔티티입니다. 신규 회원은 ACTIVE 상태의 BUYER로 생성됩니다.
 */
@Entity
@Table(
        schema = "account",
        name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounts_email", columnNames = "email")
)
public class Account {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 기존 개발 계정은 null일 수 있으나 신규 가입은 서버가 검증한 시각을 반드시 기록합니다.
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    // 같은 회원에 대한 동시 수정 시 이전 버전의 덮어쓰기를 감지합니다.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 회원 저장·삭제에 권한 변경을 함께 반영하고 관계에서 제거된 권한도 삭제합니다.
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<AccountRole> roles = new LinkedHashSet<>();

    // 가입을 저장할 때 각 테이블의 선택 정보도 같은 트랜잭션에 포함합니다.
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<AccountInterest> interests = new LinkedHashSet<>();

    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AccountNotificationSettings notificationSettings;

    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AccountConsent consent;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<AccountConsentHistory> consentHistories = new LinkedHashSet<>();

    // 가입 직후 한 번 호출합니다. 외부에서 전달한 인증 여부가 아닌 서버 검증 시각만 받습니다.
    public void initializeSignup(Instant verifiedAt, Set<InterestCategory> categories,
            boolean saleStart, boolean personalizedRecommendation, boolean marketingAgreed, Instant now) {
        if (emailVerifiedAt != null || notificationSettings != null) {
            throw new IllegalStateException("가입 초기 설정은 한 번만 적용할 수 있습니다.");
        }
        this.emailVerifiedAt = java.util.Objects.requireNonNull(verifiedAt);
        categories.forEach(category -> interests.add(new AccountInterest(this, category)));
        this.notificationSettings = new AccountNotificationSettings(this, saleStart, personalizedRecommendation);
        this.consent = new AccountConsent(this, marketingAgreed, now);
        this.consentHistories.add(new AccountConsentHistory(this, marketingAgreed, now));
    }

    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }

    // 본인 설정 변경은 계정 행 잠금 아래 실행합니다. 공통 수정 시각도 함께 갱신합니다.
    public void changeName(String name, Instant now) {
        this.name = name.trim();
        this.updatedAt = now;
    }

    // 유지할 행은 재사용하여 동일 카테고리의 삭제·재삽입에 따른 유일 제약 충돌을 피합니다.
    public void replaceInterests(Set<InterestCategory> categories, Instant now) {
        interests.removeIf(interest -> !categories.contains(interest.getCategory()));
        Set<InterestCategory> existing = getInterestCategories();
        categories.stream().filter(category -> !existing.contains(category))
                .forEach(category -> interests.add(new AccountInterest(this, category)));
        this.updatedAt = now;
    }

    // 이전 개발 계정에 설정 행이 없으면 기본 상태에서 생성합니다.
    public void changeNotifications(boolean saleStart, boolean recommendation, Instant now) {
        if (notificationSettings == null) {
            notificationSettings = new AccountNotificationSettings(this, saleStart, recommendation);
        } else {
            notificationSettings.change(saleStart, recommendation);
        }
        this.updatedAt = now;
    }

    // 같은 상태의 재요청은 이력과 시각을 바꾸지 않습니다. 최초 미동의는 철회로 기록하지 않습니다.
    public void changeMarketingConsent(boolean agreed, Instant now) {
        if (consent == null) {
            consent = new AccountConsent(this, agreed, now);
        } else {
            if (consent.isMarketingAgreed() == agreed) return;
            consent.change(agreed, now);
        }
        consentHistories.add(new AccountConsentHistory(this, agreed, now, "ACCOUNT_SETTINGS"));
        this.updatedAt = now;
    }

    // 엔티티 컬렉션을 노출하지 않고 조회 DTO에 필요한 값만 반환합니다.
    public Set<InterestCategory> getInterestCategories() {
        return interests.stream().map(AccountInterest::getCategory).collect(Collectors.toUnmodifiableSet());
    }
    public AccountNotificationSettings getNotificationSettings() { return notificationSettings; }
    public AccountConsent getConsent() { return consent; }

    // 원문은 받지 않고 서비스에서 만든 BCrypt 해시로만 변경합니다.
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
    }

    // JPA가 조회 결과로 엔티티를 만들 때 사용하는 기본 생성자입니다.
    protected Account() {
    }

    private Account(String email, String passwordHash, String name) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.name = name.trim();
        this.status = AccountStatus.ACTIVE;
        grantRole(Role.BUYER, null);
    }

    // 일반 가입 경로에서는 구매자 권한으로만 회원을 생성합니다.
    public static Account registerBuyer(String email, String passwordHash, String name) {
        return new Account(email, passwordHash, name);
    }

    // 이미 보유한 권한은 거절하고, 권한을 부여한 주체와 시각을 함께 기록합니다.
    public void grantRole(Role role, UUID grantedBy) {
        if (hasRole(role)) {
            throw new AccountException(AccountErrorCode.ROLE_ALREADY_GRANTED);
        }
        roles.add(AccountRole.grant(this, role, grantedBy, Instant.now()));
    }

    // 현재 회원이 지정한 권한을 보유하는지 확인합니다.
    public boolean hasRole(Role role) {
        return roles.stream().anyMatch(accountRole -> accountRole.getRole() == role);
    }

    // 내부 권한 컬렉션을 직접 수정하지 못하도록 읽기 전용 권한 목록을 반환합니다.
    public Set<Role> getRoles() {
        LinkedHashSet<Role> roleValues = roles.stream()
                .map(AccountRole::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(roleValues);
    }

    @PrePersist
    // 최초 저장 직전에 생성 시각과 수정 시각을 같은 값으로 기록합니다.
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    // 엔티티 변경 사항을 DB에 반영하기 전에 수정 시각을 갱신합니다.
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // 대소문자와 앞뒤 공백 차이로 같은 이메일이 중복 등록되지 않도록 정규화합니다.
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
