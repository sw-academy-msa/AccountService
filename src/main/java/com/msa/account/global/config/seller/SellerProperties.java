package com.msa.account.global.config.seller;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** PDF 업로드 상한과 서버 전용 계좌 암호화 키 설정입니다. */
@ConfigurationProperties(prefix="account.seller")
public record SellerProperties(int maxCertificateBytes, String dataEncryptionKey) {
    public SellerProperties {
        if(maxCertificateBytes<1 || maxCertificateBytes>20*1024*1024)
            throw new IllegalArgumentException("서류 상한은 1바이트 이상 20MB 이하여야 합니다.");
        dataEncryptionKey=dataEncryptionKey==null?"":dataEncryptionKey.trim();
    }
    @Override public String toString() { return "SellerProperties[REDACTED]"; }
}
