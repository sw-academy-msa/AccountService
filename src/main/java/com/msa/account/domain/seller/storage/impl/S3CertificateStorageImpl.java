package com.msa.account.domain.seller.storage.impl;
import com.msa.account.domain.seller.storage.CertificateStorage;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.global.config.seller.CertificateStorageProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
/** 비공개 객체를 저장합니다. 다운로드는 서비스에서 권한을 확인한 뒤 중계합니다. */
@Component
public class S3CertificateStorageImpl implements CertificateStorage {
    private final S3Client client;
    private final CertificateStorageProperties properties;
    private volatile boolean bucketReady;
    public S3CertificateStorageImpl(S3Client client,CertificateStorageProperties properties) {
        this.client=client; this.properties=properties;
    }
    @Override public void put(String key,CertificateContent content) {
        try {
            initializeLocalBucket();
            client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                    .contentType("application/pdf").build(),RequestBody.fromBytes(content.bytes()));
        } catch(SdkException exception) { throw unavailable(); }
    }
    @Override public byte[] get(String key) {
        try { return client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build()).asByteArray(); }
        catch(SdkException exception) { throw unavailable(); }
    }
    @Override public void delete(String key) {
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build()); }
        catch(SdkException exception) { throw unavailable(); }
    }
    // 개발 환경에서만 버킷을 자동 생성하며 운영 버킷 정책은 인프라에서 준비합니다.
    private synchronized void initializeLocalBucket() {
        if(!properties.autoCreateBucket() || bucketReady) return;
        try { client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build()); }
        catch(S3Exception e) {
            if(e.statusCode()!=404) throw e;
            try { client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build()); }
            catch(S3Exception race) {
                if(race.statusCode()!=409) throw race;
                client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
            }
        }
        bucketReady=true;
    }
    private SellerException unavailable() { return new SellerException(SellerErrorCode.CERTIFICATE_UNAVAILABLE); }
}
