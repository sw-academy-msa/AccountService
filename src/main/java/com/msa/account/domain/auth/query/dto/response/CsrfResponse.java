package com.msa.account.domain.auth.query.dto.response;

/** 프론트가 API 호스트의 쿠키를 직접 읽지 않고도 CSRF 헤더를 구성할 수 있게 합니다. */
public record CsrfResponse(String headerName, String token) { }
