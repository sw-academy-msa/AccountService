package com.msa.account.domain.membership.command.service;

import com.msa.account.domain.membership.dto.response.MembershipCancellationResponse;

import java.util.UUID;

/** 본인 멤버십 해지와 해지 취소 명령을 Outbox로 접수합니다. */
public interface MembershipCommandService {
    MembershipCancellationResponse cancel(UUID account);

    MembershipCancellationResponse withdrawCancellation(UUID account);
}
