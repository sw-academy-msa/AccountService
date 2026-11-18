package com.msa.account.domain.address.repository;

import com.msa.account.domain.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** 모든 업무 조회에 소유자 ID를 포함해 다른 회원 배송지 접근을 차단합니다. */
public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findAllByAccountIdOrderByDefaultAddressDescCreatedAtAscIdAsc(UUID accountId);
    Optional<Address> findByIdAndAccountId(UUID id, UUID accountId);
    long countByAccountId(UUID accountId);
}
