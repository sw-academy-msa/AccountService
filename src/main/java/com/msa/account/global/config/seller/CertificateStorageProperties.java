package com.msa.account.global.config.seller;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** 로컬 접속 정보와 운영 버킷 설정입니다. 운영은 EC2 IAM 역할을 사용합니다. */
@ConfigurationProperties(prefix="account.seller.storage")
public record CertificateStorageProperties(String bucket,String region,String endpoint,boolean pathStyle,
        boolean autoCreateBucket,String accessKey,String secretKey) {
    public CertificateStorageProperties {
        if(bucket==null || bucket.isBlank() || region==null || region.isBlank())
            throw new IllegalArgumentException("서류 저장 버킷과 리전이 필요합니다.");
    }
    @Override public String toString() { return "CertificateStorageProperties[REDACTED]"; }
}
