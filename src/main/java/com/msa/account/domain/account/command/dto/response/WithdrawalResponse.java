package com.msa.account.domain.account.command.dto.response;

import lombok.Builder;

import java.time.Instant;

/** 탈퇴 완료 시각만 반환합니다. 비밀번호나 계정 개인정보는 포함하지 않습니다. */
@Builder
public record WithdrawalResponse(Instant withdrawnAt) {}
