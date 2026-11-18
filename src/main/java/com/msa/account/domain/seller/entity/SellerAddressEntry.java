package com.msa.account.domain.seller.entity;

import com.msa.account.domain.seller.command.dto.request.SellerAddressEntryRequest;
import com.msa.account.domain.seller.enums.SellerAddressType;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Account 소유의 주소록입니다. Product와 Order의 과거 주소 스냅샷을 직접 변경하지 않습니다. */
@Entity
@Getter
@Table(
        schema = "account",
        name = "seller_address_entries",
        indexes =
                @Index(
                        name = "ix_seller_address_owner_type",
                        columnList = "account_id,address_type"))
public class SellerAddressEntry {
    @Id private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20, updatable = false)
    private SellerAddressType type;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Embedded private SellerLocation address;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version private Long version;

    protected SellerAddressEntry() {}

    @Builder
    public SellerAddressEntry(
            UUID id,
            UUID accountId,
            SellerAddressType type,
            SellerAddressEntryRequest request,
            Instant now) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.createdAt = now;
        update(request, now);
    }

    public void update(SellerAddressEntryRequest r, Instant now) {
        label = r.label();
        contactName = r.contactName();
        contactPhone = r.contactPhone();
        address = new SellerLocation(r.address());
        updatedAt = now;
    }

    @Override
    public String toString() {
        return "SellerAddressEntry[REDACTED]";
    }

    public static class SellerAddressEntryBuilder {
        @Override
        public String toString() {
            return "SellerAddressEntryBuilder[REDACTED]";
        }
    }
}
