package com.msa.account.domain.account.entity;

import com.msa.account.domain.account.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * 회원에게 부여한 권한과 부여 이력을 저장합니다. 한 회원은 BUYER, SELLER 등 여러 권한을 가질 수 있습니다.
 */
@Entity
@Table(
        schema = "account",
        name = "account_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_account_roles_account_id_role",
                columnNames = {"account_id", "role"}
        )
)
public class AccountRole {

    @Id
    @UuidGenerator
    private UUID id;

    // 권한에서 회원이 필요할 때 조회하며, 권한 한 건은 반드시 하나의 회원에 속합니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_account_roles_account")
    )
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    // 자동 부여한 기본 권한은 null이며, 운영자가 부여할 때는 운영자 계정 ID를 기록할 수 있습니다.
    @Column(name = "granted_by")
    private UUID grantedBy;

    protected AccountRole() {
    }

    private AccountRole(Account account, Role role, UUID grantedBy, Instant grantedAt) {
        this.account = account;
        this.role = role;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    static AccountRole grant(Account account, Role role, UUID grantedBy, Instant grantedAt) {
        return new AccountRole(account, role, grantedBy, grantedAt);
    }

    public Role getRole() {
        return role;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }
}
