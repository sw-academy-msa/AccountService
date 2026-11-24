package com.msa.account.domain.membership.query.service.impl;

import com.msa.account.domain.membership.dto.response.MembershipResponse;
import com.msa.account.domain.membership.query.service.MembershipQueryService;
import com.msa.account.domain.membership.repository.MembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/** 로컬 projection만 읽으며 Payment를 동기 호출하지 않습니다. */
@Service
@Transactional(readOnly = true)
public class MembershipQueryServiceImpl implements MembershipQueryService {
    private final MembershipRepository memberships;
    private final Clock clock;

    public MembershipQueryServiceImpl(MembershipRepository memberships, Clock clock) {
        this.memberships = memberships;
        this.clock = clock;
    }

    public MembershipResponse get(UUID account) {
        return MembershipResponse.from(memberships.findById(account).orElse(null), clock.instant());
    }

    public Map<String, Object> claim(UUID account) {
        var m = memberships.findById(account).orElse(null);
        var claim = new LinkedHashMap<String, Object>();
        // 혜택 종료 시각이 지난 자격은 상태와 무관하게 비회원으로 서명합니다.
        boolean active = m != null && m.entitled(clock.instant());
        claim.put("active", active);
        claim.put("state", m == null ? "NONE" : m.getState());
        claim.put("valid_until", active ? m.getEntitlementUntil().getEpochSecond() : 0L);
        claim.put("version", m == null ? 0L : m.getSourceVersion());
        return claim;
    }
}
