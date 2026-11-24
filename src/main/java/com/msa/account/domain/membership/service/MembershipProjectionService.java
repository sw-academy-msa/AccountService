package com.msa.account.domain.membership.service;

/** Payment 결과 이벤트를 자격 projection과 알림 Outbox로 반영합니다. */
public interface MembershipProjectionService {
    void receive(String key, byte[] payload);

    int notifyExpiring();
}
