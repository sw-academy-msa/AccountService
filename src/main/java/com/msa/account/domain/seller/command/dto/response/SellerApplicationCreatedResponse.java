package com.msa.account.domain.seller.command.dto.response;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import java.util.UUID;
/** 접수 완료 식별자와 상태를 반환하며 개인정보는 포함하지 않습니다. */
public record SellerApplicationCreatedResponse(UUID applicationId, SellerApplicationStatus status) { }
