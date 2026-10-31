package com.msa.account.domain.address.service;

import com.msa.account.domain.account.entity.Account;
import java.util.UUID;

/** Command·Query에서 공통으로 쓰는 현재 구매자 자격 검사 계약입니다. */
public interface AddressAccessService {
    Account requireBuyer(UUID accountId);
    Account requireBuyerForUpdate(UUID accountId);
}
