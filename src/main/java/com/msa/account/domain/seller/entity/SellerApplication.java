package com.msa.account.domain.seller.entity;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.seller.command.dto.request.SellerApplicationRequest;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.exception.*;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;
/** 접수 내용을 수정하지 않고 보존합니다. 재신청은 회차가 다른 새 행으로 남깁니다. */
@Entity
@Table(schema="account",name="seller_applications",
        uniqueConstraints=@UniqueConstraint(name="uk_seller_application_attempt",columnNames={"account_id","attempt_number"}),
        indexes=@Index(name="ix_seller_application_status",columnList="status,submitted_at"))
public class SellerApplication {
    @Id @UuidGenerator private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="account_id",nullable=false,updatable=false)
    private Account account;
    @Column(name="attempt_number",nullable=false,updatable=false) private int attemptNumber;
    @Column(name="seller_display_name",length=100) private String sellerDisplayName;
    @Column(name="contact_name",nullable=false,length=100) private String contactName;
    @Column(name="contact_email",nullable=false,length=320) private String contactEmail;
    @Column(name="contact_phone",nullable=false,length=20) private String contactPhone;
    @Column(name="bank_name",nullable=false,length=50) private String bankName;
    @Column(name="bank_account_encrypted",nullable=false,length=255) private String bankAccountEncrypted;
    @Column(name="bank_account_last_four",nullable=false,length=4) private String bankAccountLastFour;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="postalCode",column=@Column(name="dispatch_postal_code",nullable=false,length=5)),
        @AttributeOverride(name="roadAddress",column=@Column(name="dispatch_road_address",nullable=false,length=200)),
        @AttributeOverride(name="detailAddress",column=@Column(name="dispatch_detail_address",length=200))})
    private SellerLocation dispatchAddress;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="postalCode",column=@Column(name="return_postal_code",nullable=false,length=5)),
        @AttributeOverride(name="roadAddress",column=@Column(name="return_road_address",nullable=false,length=200)),
        @AttributeOverride(name="detailAddress",column=@Column(name="return_detail_address",length=200))})
    private SellerLocation returnAddress;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SellerApplicationStatus status;
    @Column(name="submitted_at",nullable=false,updatable=false) private Instant submittedAt;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="rejection_reason",length=1000) private String rejectionReason;
    @Version private long version;
    protected SellerApplication() { }
    public SellerApplication(Account owner,int attempt,SellerApplicationRequest request,String encrypted,Instant now) {
        account=owner; attemptNumber=attempt; sellerDisplayName=request.sellerDisplayName();
        contactName=request.contactName(); contactEmail=request.contactEmail();
        contactPhone=request.contactPhone().replace("-",""); bankName=request.bankName();
        bankAccountEncrypted=encrypted; String number=request.bankAccountNumber().replace("-","");
        bankAccountLastFour=number.substring(number.length()-4);
        dispatchAddress=new SellerLocation(request.dispatchAddress()); returnAddress=new SellerLocation(request.returnAddress());
        status=SellerApplicationStatus.PENDING; submittedAt=now;
    }
    // 동일 결론의 재요청은 최초 검토자를 덮어쓰지 않고 성공으로 취급합니다.
    public void approve(UUID reviewer,Instant now) {
        if(status==SellerApplicationStatus.APPROVED) return;
        requirePending(); status=SellerApplicationStatus.APPROVED; reviewedBy=reviewer; reviewedAt=now;
    }
    public void reject(UUID reviewer,String reason,Instant now) {
        if(status==SellerApplicationStatus.REJECTED && reason.equals(rejectionReason)) return;
        requirePending(); status=SellerApplicationStatus.REJECTED; reviewedBy=reviewer; reviewedAt=now; rejectionReason=reason;
    }
    private void requirePending() {
        if(status!=SellerApplicationStatus.PENDING) throw new SellerException(SellerErrorCode.ALREADY_REVIEWED);
    }
    public UUID getId() { return id; }
    public UUID getAccountId() { return account.getId(); }
    public int getAttemptNumber() { return attemptNumber; }
    public String getSellerDisplayName() { return sellerDisplayName; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getBankName() { return bankName; }
    // 승인 프로필 초기화용 암호문입니다. HTTP 응답 DTO에는 절대 포함하지 않습니다.
    public String getBankAccountEncrypted() { return bankAccountEncrypted; }
    public String getBankAccountLastFour() { return bankAccountLastFour; }
    public String getMaskedBankAccount() { return "***"+bankAccountLastFour; }
    public SellerLocation getDispatchAddress() { return dispatchAddress; }
    public SellerLocation getReturnAddress() { return returnAddress; }
    public SellerApplicationStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }
}
