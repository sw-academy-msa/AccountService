package com.msa.account.domain.seller.command.service;

import com.msa.account.domain.seller.command.dto.request.*;
import java.util.UUID;

/** 본인 판매자 프로필의 화면별 변경 계약입니다. 사업자등록증 교체는 포함하지 않습니다. */
public interface SellerProfileCommandService {
    void updateContact(UUID accountId, UpdateSellerContactRequest request);
    void updateBankAccount(UUID accountId, UpdateSellerBankAccountRequest request);
    void updateAddresses(UUID accountId, UpdateSellerAddressesRequest request);
}
