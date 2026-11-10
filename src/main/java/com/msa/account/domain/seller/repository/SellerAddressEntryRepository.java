package com.msa.account.domain.seller.repository;

import com.msa.account.domain.seller.entity.SellerAddressEntry;
import com.msa.account.domain.seller.enums.SellerAddressType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

/** 모든 조회에 본인 계정 조건을 적용합니다. 기본 주소의 유일성은 프로필의 단일 포인터로 보장합니다. */
public interface SellerAddressEntryRepository extends JpaRepository<SellerAddressEntry, UUID> {
    @Query(
            "select a from SellerAddressEntry a where a.accountId=:owner and a.type=:type order by"
                + " a.createdAt,a.id")
    List<SellerAddressEntry> findOwned(
            @Param("owner") UUID owner, @Param("type") SellerAddressType type);

    @Query(
            "select a from SellerAddressEntry a where a.accountId=:owner and a.type=:type and"
                + " a.id=:id")
    Optional<SellerAddressEntry> findOwned(
            @Param("owner") UUID owner,
            @Param("type") SellerAddressType type,
            @Param("id") UUID id);
}
