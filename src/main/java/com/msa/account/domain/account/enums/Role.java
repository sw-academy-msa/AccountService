package com.msa.account.domain.account.enums;

/**
 * 계정에 추가할 수 있는 권한입니다. 판매자와 운영자도 기존 구매자 권한을 유지할 수 있습니다.
 */
public enum Role {
    BUYER,
    SELLER,
    ADMIN
}
