package com.msa.account.domain.support.repository;

import com.msa.account.domain.support.entity.SupportInquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** 내 문의는 소유자 조건으로만 찾아 다른 계정의 문의 존재 여부를 드러내지 않습니다. */
public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, UUID> {
    Page<SupportInquiry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable page);

    Optional<SupportInquiry> findByIdAndAccountId(UUID id, UUID accountId);

    Page<SupportInquiry> findByStatusOrderByCreatedAtAsc(SupportInquiry.Status status, Pageable page);

    Page<SupportInquiry> findAllByOrderByCreatedAtDesc(Pageable page);
}
