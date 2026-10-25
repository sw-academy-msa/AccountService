package com.msa.account.domain.seller.repository;

import com.msa.account.domain.seller.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/** 계정 하나당 현재 판매자 프로필 하나를 저장하며 변경은 계정 잠금 아래 수행합니다. */
public interface SellerProfileRepository extends JpaRepository<SellerProfile, UUID> { }
