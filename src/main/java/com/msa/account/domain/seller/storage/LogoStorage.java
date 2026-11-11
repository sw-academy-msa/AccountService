package com.msa.account.domain.seller.storage;

/** PDF 저장 구현과 S3 클라이언트만 공유하고 이미지의 객체 경로와 형식은 별도로 관리합니다. */
public interface LogoStorage {
    void put(String key, String contentType, byte[] bytes);

    byte[] get(String key);

    void delete(String key);
}
