package com.msa.account.domain.support.service;

import com.msa.account.domain.support.dto.request.SupportRequests;
import com.msa.account.domain.support.dto.response.SupportResponses;
import com.msa.account.domain.support.entity.SupportInquiry;

import java.util.UUID;

/** 1:1 문의 작성, 본인 조회와 운영자 답변 */
public interface SupportInquiryService {
    SupportResponses.Inquiry create(UUID account, SupportRequests.Create request);

    SupportResponses.Page mine(UUID account, int page, int size);

    SupportResponses.Inquiry mineOne(UUID account, UUID id);

    /** status 없으면 전체(최근 순), WAITING이면 오래된 순으로 답변 대기만 */
    SupportResponses.Page all(SupportInquiry.Status status, int page, int size);

    SupportResponses.Inquiry answer(UUID admin, UUID id, SupportRequests.Answer request);
}
