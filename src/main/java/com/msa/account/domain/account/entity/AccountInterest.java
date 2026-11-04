package com.msa.account.domain.account.entity;

import lombok.Builder;

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
    // 기존 생성자의 기본 상태·정규화·검증을 거치는 인자만 빌더로 노출합니다.
    @Builder(access = lombok.AccessLevel.PACKAGE)
    AccountInterest(Account account, InterestCategory category) {
        this.account = account;
        this.category = category;
    }
    public InterestCategory getCategory() { return category; }

    /** 생성 중인 개인정보·토큰·계좌·해시는 문자열 출력에 포함하지 않습니다. */
    static class AccountInterestBuilder {
        @Override
        public String toString() {
            return "AccountInterestBuilder[REDACTED]";
        }
    }
}
