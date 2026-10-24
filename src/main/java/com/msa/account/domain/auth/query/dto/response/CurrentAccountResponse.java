package com.msa.account.domain.auth.query.dto.response;

import java.util.Set;
import java.util.UUID;

/** JWT의 sub로 식별한 본인 계정의 최신 정보입니다. 임의의 accountId를 입력받지 않습니다. */
public record CurrentAccountResponse(UUID accountId, String email, String name, Set<String> roles) { }
