package com.msa.account.domain.seller.command.service;
import com.msa.account.domain.seller.command.dto.request.SellerApplicationRequest;
import com.msa.account.domain.seller.command.dto.response.SellerApplicationCreatedResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
/** 신청 내용은 불변이며 거절된 계정만 새 회차로 재신청합니다. */
public interface SellerApplicationCommandService {
    SellerApplicationCreatedResponse submit(UUID accountId,SellerApplicationRequest request,List<MultipartFile> certificate);
}
