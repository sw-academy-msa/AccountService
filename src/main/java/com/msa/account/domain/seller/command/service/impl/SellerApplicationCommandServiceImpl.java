package com.msa.account.domain.seller.command.service.impl;
import com.msa.account.domain.account.enums.Role;
import com.msa.account.domain.seller.command.dto.request.SellerApplicationRequest;
import com.msa.account.domain.seller.command.dto.response.SellerApplicationCreatedResponse;
import com.msa.account.domain.seller.command.service.*;
import com.msa.account.domain.seller.entity.*;
import com.msa.account.domain.seller.enums.SellerApplicationStatus;
import com.msa.account.domain.seller.exception.*;
import com.msa.account.domain.seller.repository.*;
import com.msa.account.domain.seller.service.SellerAccessService;
import com.msa.account.domain.seller.storage.CertificateStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.*;
import java.time.Clock;
import java.util.*;
/** 신청 상태·파일 메타데이터는 로컬 DB 트랜잭션으로 저장하고 롤백 시 객체 삭제를 시도합니다. */
@Service
@Transactional
public class SellerApplicationCommandServiceImpl implements SellerApplicationCommandService {
    private static final Logger log=LoggerFactory.getLogger(SellerApplicationCommandServiceImpl.class);
    private final SellerAccessService access; private final SellerApplicationRepository applications;
    private final SellerCertificateRepository certificates; private final CertificateStorage storage;
    private final CertificateValidationService validation; private final SellerBankEncryptionService encryption;
    private final Clock clock;
    public SellerApplicationCommandServiceImpl(SellerAccessService access,SellerApplicationRepository applications,
            SellerCertificateRepository certificates,CertificateStorage storage,CertificateValidationService validation,
            SellerBankEncryptionService encryption,Clock clock) {
        this.access=access; this.applications=applications; this.certificates=certificates; this.storage=storage;
        this.validation=validation; this.encryption=encryption; this.clock=clock;
    }
    @Override public SellerApplicationCreatedResponse submit(UUID accountId,SellerApplicationRequest request,List<MultipartFile> files) {
        var owner=access.requireBuyer(accountId,true);
        var previous=applications.findFirstByAccount_IdOrderByAttemptNumberDesc(accountId);
        if(owner.hasRole(Role.SELLER) || previous.filter(a->a.getStatus()!=SellerApplicationStatus.REJECTED).isPresent())
            throw new SellerException(SellerErrorCode.ALREADY_APPLIED);
        var content=validation.validate(files);
        var application=applications.save(new SellerApplication(owner,previous.map(a->a.getAttemptNumber()+1).orElse(1),
                request,encryption.encrypt(request.bankAccountNumber()),clock.instant()));
        String key="seller-certificates/"+application.getId()+"/"+UUID.randomUUID()+".pdf";
        // 업로드가 타임아웃으로 불확실해져도 알려진 키를 사용해 롤백 후 삭제를 시도합니다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK) {
                    try { storage.delete(key); }
                    catch(RuntimeException exception) { log.warn("Certificate cleanup failed: applicationId={}",application.getId()); }
                }
            }
        });
        storage.put(key,content);
        certificates.save(new SellerCertificate(application.getId(),key,content.bytes().length));
        return new SellerApplicationCreatedResponse(application.getId(),application.getStatus());
    }
}
