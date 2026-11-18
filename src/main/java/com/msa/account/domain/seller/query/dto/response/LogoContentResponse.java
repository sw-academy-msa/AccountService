package com.msa.account.domain.seller.query.dto.response;

import lombok.Builder;

/** HTTP 본문으로 중계할 내부 반환 DTO입니다. 원본 객체 키를 클라이언트에 전달하지 않습니다. */
@Builder
public record LogoContentResponse(String contentType, byte[] bytes) {
    public LogoContentResponse {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public String toString() {
        return "LogoContentResponse[REDACTED]";
    }

    public static class LogoContentResponseBuilder {
        @Override
        public String toString() {
            return "LogoContentResponseBuilder[REDACTED]";
        }
    }
}
