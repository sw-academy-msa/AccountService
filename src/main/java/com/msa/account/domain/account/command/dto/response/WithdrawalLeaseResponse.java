package com.msa.account.domain.account.command.dto.response;

import lombok.Builder;

import java.util.UUID;

/** 내부 반환 DTO입니다. HTTP로 작업 소유권을 노출하지 않습니다. */
@Builder
public record WithdrawalLeaseResponse(UUID accountId, UUID operationId) {}
