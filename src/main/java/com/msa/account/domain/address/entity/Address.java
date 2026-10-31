package com.msa.account.domain.address.entity;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.address.command.dto.request.AddressRequest;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** 구매자의 저장 배송지입니다. 주문 당시 배송지 스냅샷은 Order가 별도로 소유합니다. */
@Entity
@Table(schema = "account", name = "addresses",
        indexes = @Index(name = "ix_addresses_account", columnList = "account_id"))
public class Address {
    @Id @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;
    @Column(nullable = false, length = 50)
    private String label;
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;
    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;
    @Column(name = "postal_code", nullable = false, length = 5)
    private String postalCode;
    @Column(name = "road_address", nullable = false, length = 200)
    private String roadAddress;
    @Column(name = "detail_address", length = 200)
    private String detailAddress;
    @Column(name = "delivery_memo", length = 500)
    private String deliveryMemo;
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Address() { }

    public Address(Account owner, AddressRequest request, boolean defaultAddress, Instant now) {
        this.account = owner; this.defaultAddress = defaultAddress; this.createdAt = now;
        update(request, now);
    }

    // 소유자와 기본 여부는 일반 정보 수정으로 바꿀 수 없습니다.
    public void update(AddressRequest request, Instant now) {
        label = request.label(); recipientName = request.recipientName();
        recipientPhone = request.recipientPhone().replace("-", "");
        postalCode = request.postalCode(); roadAddress = request.roadAddress();
        detailAddress = request.detailAddress(); deliveryMemo = request.deliveryMemo();
        updatedAt = now;
    }

    // 기본 지정은 서비스가 계정 행 잠금을 획득한 뒤 같은 트랜잭션에서만 수행합니다.
    public void setDefault(boolean value, Instant now) {
        if (defaultAddress != value) { defaultAddress = value; updatedAt = now; }
    }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getPostalCode() { return postalCode; }
    public String getRoadAddress() { return roadAddress; }
    public String getDetailAddress() { return detailAddress; }
    public String getDeliveryMemo() { return deliveryMemo; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
