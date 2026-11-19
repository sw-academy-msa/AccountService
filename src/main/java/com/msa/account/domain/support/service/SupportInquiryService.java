package com.msa.account.domain.support.service;

import com.msa.account.domain.notification.service.BusinessNoticeService;
import com.msa.account.domain.support.dto.SupportDtos;
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
public class SupportInquiryService {
    private final SupportInquiryRepository inquiries;
    private final BusinessNoticeService notices;
    private final Clock clock;

    public SupportInquiryService(
            SupportInquiryRepository inquiries, BusinessNoticeService notices, Clock clock) {
        this.inquiries = inquiries;
        this.notices = notices;
        this.clock = clock;
    }

    public SupportDtos.Inquiry create(UUID account, SupportDtos.Create r) {
        var saved =
                inquiries.save(
                        new SupportInquiry(
                                account,
                                r.category(),
                                r.title().trim(),
                                r.body().trim(),
                                r.orderId(),
                                clock.instant()));
        return SupportDtos.Inquiry.of(saved, false);
    }

    @Transactional(readOnly = true)
    public SupportDtos.Page mine(UUID account, int page, int size) {
        return SupportDtos.Page.of(
                inquiries.findByAccountIdOrderByCreatedAtDesc(account, paging(page, size)), false);
    }

    @Transactional(readOnly = true)
    public SupportDtos.Inquiry mineOne(UUID account, UUID id) {
        return SupportDtos.Inquiry.of(
                inquiries
                        .findByIdAndAccountId(id, account)
                        .orElseThrow(() -> new SupportException(SupportErrorCode.NOT_FOUND)),
                false);
    }

    /** status 없으면 전체(최근 순), WAITING이면 오래된 순으로 답변 대기만 */
    @Transactional(readOnly = true)
    public SupportDtos.Page all(SupportInquiry.Status status, int page, int size) {
        var p = paging(page, size);
        return SupportDtos.Page.of(
                status == null
                        ? inquiries.findAllByOrderByCreatedAtDesc(p)
                        : inquiries.findByStatusOrderByCreatedAtAsc(status, p),
                true);
    }

    public SupportDtos.Inquiry answer(UUID admin, UUID id, SupportDtos.Answer r) {
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
        return SupportDtos.Inquiry.of(s, true);
    }

    private PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new SupportException(SupportErrorCode.INVALID);
        return PageRequest.of(page, size);
    }
}
