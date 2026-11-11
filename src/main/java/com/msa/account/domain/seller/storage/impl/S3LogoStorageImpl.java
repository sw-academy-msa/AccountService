package com.msa.account.domain.seller.storage.impl;

import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.storage.LogoStorage;
import com.msa.account.global.config.seller.CertificateStorageProperties;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** 승인 서류와 동일한 비공개 버킷을 쓰되 seller-logos 접두사 아래에서만 접근합니다. */
@Component
public class S3LogoStorageImpl implements LogoStorage {
    private final S3Client client;
    private final CertificateStorageProperties properties;

    public S3LogoStorageImpl(S3Client client, CertificateStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    private void key(String key) {
        if (key == null || !key.startsWith("seller-logos/"))
            throw new SellerException(SellerErrorCode.INVALID_LOGO);
    }

    private synchronized void localBucket() {
        if (!properties.autoCreateBucket()) return;
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) throw ex;
            try {
                client.createBucket(
                        CreateBucketRequest.builder().bucket(properties.bucket()).build());
            } catch (S3Exception race) {
                if (race.statusCode() != 409) throw race;
            }
        }
    }

    public void put(String key, String type, byte[] bytes) {
        key(key);
        try {
            localBucket();
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(type)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException ex) {
            throw unavailable();
        }
    }

    public byte[] get(String key) {
        key(key);
        try {
            return client.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                    .asByteArray();
        } catch (SdkException ex) {
            throw unavailable();
        }
    }

    public void delete(String key) {
        key(key);
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (SdkException ex) {
            throw unavailable();
        }
    }

    private SellerException unavailable() {
        return new SellerException(SellerErrorCode.LOGO_UNAVAILABLE);
    }
}
