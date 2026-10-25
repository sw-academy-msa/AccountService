package com.msa.account.domain.seller.command.service;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
/** 파일 개수·크기·형식 확인을 신청 처리와 분리합니다. */
public interface CertificateValidationService { CertificateContent validate(List<MultipartFile> files); }
