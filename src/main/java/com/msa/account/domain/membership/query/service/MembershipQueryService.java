package com.msa.account.domain.membership.query.service;

import com.msa.account.domain.membership.dto.response.MembershipResponse;

import java.util.*;

/** 본인 자격 조회와 사용자 JWT의 membership claim을 제공합니다. */
public interface MembershipQueryService {
    MembershipResponse get(UUID account);

    Map<String, Object> claim(UUID account);
}
