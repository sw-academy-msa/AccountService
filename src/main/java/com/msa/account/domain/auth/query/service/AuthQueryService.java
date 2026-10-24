package com.msa.account.domain.auth.query.service;

import com.msa.account.domain.auth.query.dto.response.CurrentAccountResponse;
import java.util.UUID;

/** 로그인한 본인의 최신 회원 상태를 조회하는 계약입니다. */
public interface AuthQueryService {
    CurrentAccountResponse currentAccount(UUID id);
}
