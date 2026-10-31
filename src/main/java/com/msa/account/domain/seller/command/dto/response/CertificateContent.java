package com.msa.account.domain.seller.command.dto.response;
/** 업로드 검증·저장·다운로드 사이에서만 쓰는 내부 DTO입니다. JSON 응답으로 직렬화하지 않습니다. */
public record CertificateContent(String fileName, byte[] bytes) {
    public CertificateContent { bytes=bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public String toString() { return "CertificateContent[REDACTED]"; }
}
