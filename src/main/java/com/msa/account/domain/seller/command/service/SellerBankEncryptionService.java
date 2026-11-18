package com.msa.account.domain.seller.command.service;
/** 정산 기능 없이 계좌번호를 보관하며 평문 저장을 방지하는 계약입니다. */
public interface SellerBankEncryptionService { String encrypt(String accountNumber); }
