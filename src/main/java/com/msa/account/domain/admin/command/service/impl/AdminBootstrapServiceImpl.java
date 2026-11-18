package com.msa.account.domain.admin.command.service.impl;
import com.msa.account.domain.admin.command.service.AdminBootstrapService;
import com.msa.account.domain.account.repository.AccountRepository;
import com.msa.account.domain.account.enums.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Locale;
/** 운영자 지정은 기존 회원에 ADMIN만 추가하며 사용자 입력 API에서는 호출하지 않습니다. */
@Service
@Transactional
public class AdminBootstrapServiceImpl implements AdminBootstrapService {
    private final AccountRepository accounts; private final Clock clock;
    public AdminBootstrapServiceImpl(AccountRepository accounts,Clock clock) { this.accounts=accounts; this.clock=clock; }
    @Override public void grant(String email) {
        if(email==null || email.isBlank()) throw new IllegalStateException("최초 운영자 이메일을 지정해야 합니다.");
        var id=accounts.findIdByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(()->new IllegalStateException("기존 회원가입을 완료한 계정이 필요합니다."));
        var account=accounts.findLockedById(id).orElseThrow();
        if(account.getStatus()!=AccountStatus.ACTIVE || account.getEmailVerifiedAt()==null)
            throw new IllegalStateException("인증 완료된 활성 계정이 필요합니다.");
        if(!account.hasRole(Role.ADMIN)) account.grantRole(Role.ADMIN,null,clock.instant());
    }
}
