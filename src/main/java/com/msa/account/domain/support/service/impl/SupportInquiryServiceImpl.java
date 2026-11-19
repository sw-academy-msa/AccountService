package com.msa.account.domain.support.service.impl;

import com.msa.account.domain.support.service.SupportInquiryService;

import com.msa.account.domain.notification.service.BusinessNoticeService;
import com.msa.account.domain.support.dto.request.SupportRequests;
import com.msa.account.domain.support.dto.response.SupportResponses;
import com.msa.account.domain.support.entity.SupportInquiry;
import com.msa.account.domain.support.exception.*;
import com.msa.account.domain.support.repository.SupportInquiryRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 1:1 문의 작성·조회와 운영자 답변. 답변하면 문의한 계정에 알림을 남깁니다. */
@Service
@Transactional
public class SupportInquiryServiceImpl implements SupportInquiryService {
    private final SupportInquiryRepository inquiries;
    private final BusinessNoticeService notices;
    private final Clock clock;

    public SupportInquiryServiceImpl(
            SupportInquiryRepository inquiries, BusinessNoticeService notices, Clock clock) {
        this.inquiries = inquiries;
        this.notices = notices;
        this.clock = clock;
    }

    public SupportResponses.Inquiry create(UUID account, SupportRequests.Create r) {
        var saved =
                inquiries.save(
                        new SupportInquiry(
                                account,
                                r.category(),
                                r.title().trim(),
                                r.body().trim(),
                                r.orderId(),
                                clock.instant()));
        return SupportResponses.Inquiry.of(saved, false);
    }

    @Transactional(readOnly = true)
    public SupportResponses.Page mine(UUID account, int page, int size) {
        return SupportResponses.Page.of(
                inquiries.findByAccountIdOrderByCreatedAtDesc(account, paging(page, size)), false);
    }

    @Transactional(readOnly = true)
    public SupportResponses.Inquiry mineOne(UUID account, UUID id) {
        return SupportResponses.Inquiry.of(
                inquiries
                        .findByIdAndAccountId(id, account)
                        .orElseThrow(() -> new SupportException(SupportErrorCode.NOT_FOUND)),
                false);
    }

    /** status 없으면 전체(최근 순), WAITING이면 오래된 순으로 답변 대기만 */
    @Transactional(readOnly = true)
    public SupportResponses.Page all(SupportInquiry.Status status, int page, int size) {
        var p = paging(page, size);
        return SupportResponses.Page.of(
                status == null
                        ? inquiries.findAllByOrderByCreatedAtDesc(p)
                        : inquiries.findByStatusOrderByCreatedAtAsc(status, p),
                true);
    }

    public SupportResponses.Inquiry answer(UUID admin, UUID id, SupportRequests.Answer r) {
        var s =
                inquiries
                        .findById(id)
                        .orElseThrow(() -> new SupportException(SupportErrorCode.NOT_FOUND));
        if (!s.answer(admin, r.answer().trim(), clock.instant())) {
            throw new SupportException(SupportErrorCode.ALREADY_ANSWERED);
        }
        inquiries.flush();
        notices.enqueue(
                s.getAccountId(),
                "SUPPORT_ANSWERED",
                s.getId(),
                s.getVersion(),
                "SUPPORT_INQUIRY",
                s.getId(),
                null,
                null);
        return SupportResponses.Inquiry.of(s, true);
    }

    private PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new SupportException(SupportErrorCode.INVALID);
        return PageRequest.of(page, size);
    }
}
