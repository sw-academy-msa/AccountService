package com.msa.account.domain.account.query.dto.response;

import lombok.Builder;

/** 내부 Notification 전용 최소 응답입니다. 이메일이나 주소는 전달하지 않습니다. */
@Builder
public record NotificationEligibilityResponse(boolean eligible) {}
