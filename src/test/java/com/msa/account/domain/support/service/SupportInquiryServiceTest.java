package com.msa.account.domain.support.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.msa.account.domain.notification.service.BusinessNoticeService;
import com.msa.account.domain.support.dto.request.SupportRequests;
import com.msa.account.domain.support.dto.response.SupportResponses;
import com.msa.account.domain.support.entity.SupportInquiry;
import com.msa.account.domain.support.exception.SupportException;
import com.msa.account.domain.support.repository.SupportInquiryRepository;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

/** 1:1 문의: 본인 것만 조회, 답변은 한 번, 답변하면 문의한 계정에 알림 */
class SupportInquiryServiceTest {
    private final SupportInquiryRepository repo = mock(SupportInquiryRepository.class);
    private final BusinessNoticeService notices = mock(BusinessNoticeService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T01:00:00Z"), ZoneOffset.UTC);
    private final SupportInquiryService service = new com.msa.account.domain.support.service.impl.SupportInquiryServiceImpl(repo, notices, clock);
    private final UUID buyer = UUID.randomUUID(), admin = UUID.randomUUID();

    @Test
    void createTrimsAndStartsWaiting() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = service.create(buyer, new SupportRequests.Create(SupportInquiry.Category.PAYMENT, " 제목 ", " 내용 ", null));
        assertThat(r.title()).isEqualTo("제목");
        assertThat(r.body()).isEqualTo("내용");
        assertThat(r.status()).isEqualTo(SupportInquiry.Status.WAITING);
        assertThat(r.accountId()).isNull(); // 본인 응답에는 계정 id를 넣지 않는다
    }

    @Test
    void othersInquiryIsNotFound() {
        when(repo.findByIdAndAccountId(any(), eq(buyer))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.mineOne(buyer, UUID.randomUUID())).isInstanceOf(SupportException.class);
    }

    @Test
    void answerOnceAndNotifyInquirer() {
        var s = new SupportInquiry(buyer, SupportInquiry.Category.ETC, "t", "b", null, clock.instant());
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));
        var r = service.answer(admin, s.getId(), new SupportRequests.Answer("답변"));
        assertThat(r.status()).isEqualTo(SupportInquiry.Status.ANSWERED);
        assertThat(r.answer()).isEqualTo("답변");
        verify(notices).enqueue(eq(buyer), eq("SUPPORT_ANSWERED"), eq(s.getId()), anyLong(), eq("SUPPORT_INQUIRY"), eq(s.getId()), isNull(), isNull());
        assertThatThrownBy(() -> service.answer(admin, s.getId(), new SupportRequests.Answer("또")))
                .isInstanceOf(SupportException.class);
    }
}
