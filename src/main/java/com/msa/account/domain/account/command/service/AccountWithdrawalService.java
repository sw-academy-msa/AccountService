package com.msa.account.domain.account.command.service;

import com.msa.account.domain.account.command.dto.request.WithdrawAccountRequest;
import com.msa.account.domain.account.command.dto.response.WithdrawalResponse;
import com.msa.account.domain.account.query.dto.response.WithdrawalEligibilityResponse;

import java.util.UUID;

/** 회원 탈퇴의 본인 인증과 로컬 상태 변경 계약입니다. 다른 서비스의 이력을 삭제하지 않습니다. */
public interface AccountWithdrawalService {
    WithdrawalEligibilityResponse eligibility(UUID owner);

    WithdrawalResponse withdraw(UUID owner, WithdrawAccountRequest request);
}
