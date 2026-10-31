package com.msa.account.domain.account.query.service.impl;

import com.msa.account.domain.account.query.service.AccountQueryService;

import com.msa.account.domain.account.enums.InterestCategory;
import com.msa.account.domain.account.query.dto.response.SignupOptionsResponse;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

/** 가입 시 사용할 고정 선택지를 제공합니다. 별도 Query DB는 만들지 않습니다. */
@Service
public class AccountQueryServiceImpl implements AccountQueryService {
    @Override
    // 확정된 MVP 알림 두 종류만 안내하고 광고성 동의가 별도 조건임을 명시합니다.
    public SignupOptionsResponse signupOptions() {
        return new SignupOptionsResponse(
                Arrays.stream(InterestCategory.values())
                        .map(category -> new SignupOptionsResponse.CategoryOption(category.name(), category.getLabel()))
                        .toList(),
                List.of("SALE_START", "PERSONALIZED_RECOMMENDATION"), true);
    }
}
