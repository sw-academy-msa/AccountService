package com.msa.account.domain.seller.command.service.impl;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import com.msa.account.domain.seller.command.service.CertificateValidationService;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.global.config.seller.SellerProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** 확장자·MIME·PDF 서명을 확인합니다. 악성 문서 검사나 사업자 진위 확인을 대신하지 않습니다. */
@Service
public class CertificateValidationServiceImpl implements CertificateValidationService {
    private final SellerProperties properties;
    public CertificateValidationServiceImpl(SellerProperties properties) { this.properties=properties; }
    @Override public CertificateContent validate(List<MultipartFile> files) {
        if(files==null || files.size()!=1 || files.getFirst()==null || files.getFirst().isEmpty())
            throw new SellerException(SellerErrorCode.CERTIFICATE_REQUIRED);
        var file=files.getFirst();
        if(file.getSize()>properties.maxCertificateBytes()) throw new SellerException(SellerErrorCode.CERTIFICATE_TOO_LARGE);
        if(file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !"application/pdf".equalsIgnoreCase(file.getContentType())) throw invalid();
        try {
            // getBytes 대신 상한+1만 읽어 파일 구현이 크기를 잘못 보고해도 무제한 메모리를 쓰지 않습니다.
            byte[] bytes;
            try(var stream=file.getInputStream()) { bytes=stream.readNBytes(properties.maxCertificateBytes()+1); }
            if(bytes.length>properties.maxCertificateBytes()) throw new SellerException(SellerErrorCode.CERTIFICATE_TOO_LARGE);
            String header=new String(bytes,0,Math.min(5,bytes.length),StandardCharsets.US_ASCII);
            String tail=new String(bytes,Math.max(0,bytes.length-1024),Math.min(bytes.length,1024),StandardCharsets.US_ASCII);
            if(!header.equals("%PDF-") || !tail.contains("%%EOF")) throw invalid();
            return new CertificateContent("business-registration.pdf",bytes);
        } catch(IOException exception) { throw invalid(); }
    }
    private SellerException invalid() { return new SellerException(SellerErrorCode.INVALID_CERTIFICATE); }
}
