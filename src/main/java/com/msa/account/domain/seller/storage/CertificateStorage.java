package com.msa.account.domain.seller.storage;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
/** 객체 원문 저장 계약입니다. 저장 키는 서버에서 생성하며 외부 입력으로 받지 않습니다. */
public interface CertificateStorage {
    void put(String key,CertificateContent content);
    byte[] get(String key);
    void delete(String key);
}
