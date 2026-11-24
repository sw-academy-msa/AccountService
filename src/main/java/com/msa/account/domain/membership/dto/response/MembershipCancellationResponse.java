package com.msa.account.domain.membership.dto.response;

import java.util.UUID;

/** 해지 명령 접수 결과입니다. 금융 처리 완료는 sourceVersion 변경으로 확인합니다. */
public record MembershipCancellationResponse(
        UUID commandId, String status, boolean refreshRequired) {}
