package com.msa.account.domain.auth.command.dto.response;

import java.util.*;

/** 내부 인증 결과이며 HTTP 응답이 아닙니다. 비밀값/해시는 인증 주체에 남기지 않습니다. */
public record ServiceClientPrincipal(UUID subject, String clientId, Set<String> scopes) {}
