package com.msa.account.domain.seller.entity;
import jakarta.persistence.*;
import java.util.UUID;
/** PDF 원문은 S3에 저장하며 DB에는 비공개 저장 키와 파일 정보만 보존합니다. */
@Entity
@Table(schema="account",name="seller_certificates")
public class SellerCertificate {
    @Id private UUID applicationId;
    @Column(name="object_key",nullable=false,unique=true,length=200) private String objectKey;
    @Column(name="file_name",nullable=false,length=100) private String fileName;
    @Column(name="content_length",nullable=false) private long contentLength;
    protected SellerCertificate() { }
    public SellerCertificate(UUID applicationId,String key,long length) {
        this.applicationId=applicationId; objectKey=key; fileName="business-registration.pdf"; contentLength=length;
    }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public long getContentLength() { return contentLength; }
}
