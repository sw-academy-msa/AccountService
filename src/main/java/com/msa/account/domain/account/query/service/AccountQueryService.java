package com.msa.account.domain.account.query.service;

import com.msa.account.domain.account.query.dto.response.SignupOptionsResponse;

/** Account 조회 기능의 계약입니다. Command와 같은 애플리케이션에서 실행합니다. */
public interface AccountQueryService {
    SignupOptionsResponse signupOptions();
}
