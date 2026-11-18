package com.msa.account.domain.verification.command.dto.response;

/** HTTP 응답이 아닌 렌더링 내부 결과입니다. 같은 인증 메일의 일반 텍스트와 HTML을 전달합니다. */
public record VerificationEmailContent(String plainText, String html) { }
