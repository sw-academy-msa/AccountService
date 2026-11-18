package com.msa.account.domain.admin.command.service;
/** 공개 API 없이 기존 인증 완료 회원을 최초 운영자로 지정하는 관리용 계약입니다. */
public interface AdminBootstrapService { void grant(String email); }
