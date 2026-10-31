package com.msa.account.domain.account.entity;

import com.msa.account.domain.account.enums.InterestCategory;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

/** 선택한 카테고리당 한 행을 저장합니다. 건너뛰면 해당 회원의 관심 행이 없습니다. */
@Entity
@Table(schema = "account", name = "account_interests",
        uniqueConstraints = @UniqueConstraint(name = "uk_account_interests_category",
                columnNames = {"account_id", "category"}))
public class AccountInterest {
    @Id @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InterestCategory category;

    protected AccountInterest() { }

    // 회원 엔티티에서만 생성하여 관심 정보의 소유자를 일관되게 지정합니다.
    AccountInterest(Account account, InterestCategory category) {
        this.account = account;
        this.category = category;
    }
    public InterestCategory getCategory() { return category; }
}
