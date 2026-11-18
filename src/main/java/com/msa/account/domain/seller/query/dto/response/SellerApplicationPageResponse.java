package com.msa.account.domain.seller.query.dto.response;
import org.springframework.data.domain.Page;
import java.util.List;
/** Spring 내부 Page 구현에 종속되지 않는 운영자 목록 응답입니다. */
public record SellerApplicationPageResponse(List<SellerApplicationResponse> content,int page,int size,long totalElements,int totalPages) {
    public static SellerApplicationPageResponse from(Page<SellerApplicationResponse> page) {
        return new SellerApplicationPageResponse(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
}
