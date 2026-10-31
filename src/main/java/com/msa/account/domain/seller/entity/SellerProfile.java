package com.msa.account.domain.seller.entity;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.seller.command.dto.request.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 승인 후 수정 가능한 현재 정보입니다. 원본 신청서와 승인 서류는 그대로 보존합니다. */
@Entity
@Table(schema = "account", name = "seller_profiles")
public class SellerProfile {
    @Id
    private UUID accountId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_application_id", nullable = false, updatable = false, unique = true)
    private SellerApplication approvedApplication;
    @Column(name = "seller_display_name", length = 100)
    private String sellerDisplayName;
    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;
    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;
    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;
    @Column(name = "bank_account_encrypted", nullable = false, length = 255)
    private String bankAccountEncrypted;
    @Column(name = "bank_account_last_four", nullable = false, length = 4)
    private String bankAccountLastFour;
    // 기존 신청 폼은 예금주를 수집하지 않았으므로 최초에는 null일 수 있습니다.
    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "postalCode", column = @Column(name = "dispatch_postal_code", nullable = false, length = 5)),
        @AttributeOverride(name = "roadAddress", column = @Column(name = "dispatch_road_address", nullable = false, length = 200)),
        @AttributeOverride(name = "detailAddress", column = @Column(name = "dispatch_detail_address", length = 200))})
    private SellerLocation dispatchAddress;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "postalCode", column = @Column(name = "return_postal_code", nullable = false, length = 5)),
        @AttributeOverride(name = "roadAddress", column = @Column(name = "return_road_address", nullable = false, length = 200)),
        @AttributeOverride(name = "detailAddress", column = @Column(name = "return_detail_address", length = 200))})
    private SellerLocation returnAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected SellerProfile() { }

    // 계정 행 잠금을 획득한 승인·최초 수정 트랜잭션에서만 생성합니다.
    public SellerProfile(Account owner, SellerApplication approved, Instant now) {
        if (approved.getStatus() != SellerApplicationStatus.APPROVED || !owner.getId().equals(approved.getAccountId()))
            throw new IllegalArgumentException("본인의 승인된 신청서가 필요합니다.");
        account = owner; accountId = owner.getId(); approvedApplication = approved;
        sellerDisplayName = approved.getSellerDisplayName(); contactName = approved.getContactName();
        contactEmail = approved.getContactEmail(); contactPhone = approved.getContactPhone();
        bankName = approved.getBankName(); bankAccountEncrypted = approved.getBankAccountEncrypted();
        bankAccountLastFour = approved.getBankAccountLastFour();
        dispatchAddress = new SellerLocation(approved.getDispatchAddress());
        returnAddress = new SellerLocation(approved.getReturnAddress());
        createdAt = now; updatedAt = now;
    }

    // 화면별 수정 범위를 제한하여 동시에 다른 정보를 수정해도 서로 덮어쓰지 않습니다.
    public void updateContact(UpdateSellerContactRequest request, Instant now) {
        sellerDisplayName = request.sellerDisplayName(); contactName = request.contactName();
        contactEmail = request.contactEmail(); contactPhone = request.contactPhone().replace("-", "");
        updatedAt = now;
    }

    // 원문 계좌번호는 엔티티에 저장하지 않고 서비스가 만든 암호문만 받습니다.
    public void updateBank(UpdateSellerBankAccountRequest request, String encrypted, Instant now) {
        bankName = request.bankName(); bankAccountEncrypted = encrypted;
        String number = request.bankAccountNumber();
        bankAccountLastFour = number.substring(number.length() - 4);
        accountHolderName = request.accountHolderName(); updatedAt = now;
    }

    public void updateAddresses(UpdateSellerAddressesRequest request, Instant now) {
        dispatchAddress = new SellerLocation(request.dispatchAddress());
        returnAddress = new SellerLocation(request.returnAddress()); updatedAt = now;
    }

    public UUID getAccountId() { return accountId; }
    public SellerApplication getApprovedApplication() { return approvedApplication; }
    public String getSellerDisplayName() { return sellerDisplayName; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getBankName() { return bankName; }
    public String getMaskedBankAccount() { return "***" + bankAccountLastFour; }
    public String getAccountHolderName() { return accountHolderName; }
    public SellerLocation getDispatchAddress() { return dispatchAddress; }
    public SellerLocation getReturnAddress() { return returnAddress; }
    public Instant getUpdatedAt() { return updatedAt; }
}
