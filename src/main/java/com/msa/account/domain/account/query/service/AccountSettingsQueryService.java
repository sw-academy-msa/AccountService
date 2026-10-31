package com.msa.account.domain.account.query.service;
import com.msa.account.domain.account.query.dto.response.AccountSettingsResponse;
import java.util.UUID;
/** 인증된 회원의 최신 프로필과 설정을 조회하는 계약입니다. */
public interface AccountSettingsQueryService {
    AccountSettingsResponse getSettings(UUID accountId);
}
