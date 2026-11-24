package com.msa.account.domain.membership.command.service.impl;

import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.membership.command.service.MembershipCommandService;
import com.msa.account.domain.membership.dto.response.MembershipCancellationResponse;
import com.msa.account.domain.membership.exception.*;
import com.msa.account.domain.membership.repository.MembershipRepository;
import com.msa.account.domain.outbox.entity.OutboxEvent;
import com.msa.account.domain.outbox.repository.OutboxEventRepository;
import com.msa.account.global.config.membership.MembershipProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.*;

/** 같은 세대의 해지는 한 번만 명령을 만들고 재요청에는 처음 명령 ID를 돌려줍니다. */
@Service
public class MembershipCommandServiceImpl implements MembershipCommandService {
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final OutboxEventRepository outbox;
    private final MembershipProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public MembershipCommandServiceImpl(
            MembershipRepository memberships,
            AccountRepository accounts,
            OutboxEventRepository outbox,
            MembershipProperties properties,
            ObjectMapper json,
            Clock clock) {
        this.memberships = memberships;
        this.accounts = accounts;
        this.outbox = outbox;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public MembershipCancellationResponse cancel(UUID account) {
        if (!properties.apiEnabled())
            throw new MembershipException(MembershipErrorCode.UNAVAILABLE);
        // 토큰 만료 전이라도 탈퇴했거나 BUYER 권한이 없는 계정은 현재 DB 기준으로 거절합니다.
        var owner = accounts.findById(account).orElse(null);
        if (owner == null
                || owner.getStatus() != AccountStatus.ACTIVE
                || !owner.getRoles().contains(Role.BUYER))
            throw new MembershipException(MembershipErrorCode.NOT_CANCELABLE);
        var m =
                memberships
                        .lock(account)
                        .orElseThrow(
                                () -> new MembershipException(MembershipErrorCode.NOT_CANCELABLE));
        boolean requested =
                m.getCancelGeneration() != null && m.getCancelGeneration() == m.getGeneration();
        if (!requested && (!m.isAutoRenew() || !Set.of("ACTIVE", "GRACE").contains(m.getState())))
            throw new MembershipException(MembershipErrorCode.NOT_CANCELABLE);
        var now = clock.instant();
        UUID id = m.requestCancel(now);
        if (!requested) {
            var command = new LinkedHashMap<String, Object>();
            command.put("eventId", id);
            command.put("schemaVersion", 1);
            command.put("type", "MEMBERSHIP_CANCEL_REQUESTED");
            command.put("accountId", account);
            command.put("subscriptionId", m.getSubscriptionId());
            command.put("generation", m.getGeneration());
            command.put("commandVersion", m.getCommandVersion());
            command.put("occurredAt", now);
            // 계정 단위 파티션 키로 같은 계정의 명령 순서를 유지합니다.
            outbox.save(
                    OutboxEvent.builder()
                            .id(id)
                            .topic("stockdrop.membership.commands.private.v1")
                            .aggregateId(account)
                            .payload(json.writeValueAsString(command))
                            .now(now)
                            .build());
        }
        return new MembershipCancellationResponse(id, "PENDING", true);
    }
}
