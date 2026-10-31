package com.msa.account.domain.account.command.service.impl;

import com.msa.account.domain.account.command.service.AccountCommandService;

import com.msa.account.domain.account.command.dto.request.SignupRequest;
import com.msa.account.domain.account.command.dto.response.SignupResponse;
import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.exception.AccountErrorCode;
import com.msa.account.domain.account.exception.AccountException;
import com.msa.account.domain.account.repository.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.Clock;
import java.time.Instant;
import com.msa.account.domain.verification.command.service.EmailVerificationService;

/**
 * 이메일 인증 증명 소비, 비밀번호 해시 생성, 회원·초기 설정 저장을 한 트랜잭션에서 처리합니다.
 */
@Service
@Transactional(readOnly = true)
public class AccountCommandServiceImpl implements AccountCommandService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;

    public AccountCommandServiceImpl(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.clock = clock;
    }

    @Override
    @Transactional
    // 회원가입 요청을 처리하고 가입 결과를 반환합니다.
    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        // 증명 소비와 아래 회원·선택 정보 저장은 함께 커밋하거나 함께 롤백합니다.
        Instant verifiedAt = emailVerificationService.consume(normalizedEmail, request.verificationToken());
        validateEmailAvailability(normalizedEmail);

        Account account = Account.registerBuyer(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.name()
        );
        account.initializeSignup(verifiedAt, request.interestCategories(),
                request.notificationSettings().saleStart(),
                request.notificationSettings().personalizedRecommendation(),
                request.marketingConsent(), clock.instant());

        // 즉시 flush하여 DB 제약조건 위반을 이 메서드 안에서 확인합니다.
        try {
            return SignupResponse.from(accountRepository.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            // 이메일 유니크 위반만 해당 도메인 오류로 변환하고 다른 제약 위반은 공통 처리에 맡깁니다.
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uk_accounts_email".equals(violation.getConstraintName())) {
                    throw new AccountException(AccountErrorCode.EMAIL_ALREADY_EXISTS);
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }

    // 빠른 오류 응답을 위한 사전 검사입니다. 동시 가입에 대한 최종 중복 방지는 DB 유니크 제약이 담당합니다.
    private void validateEmailAvailability(String email) {
        if (accountRepository.existsByEmail(email)) {
            throw new AccountException(AccountErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    // 대소문자와 앞뒤 공백 차이로 같은 이메일이 중복 등록되지 않도록 정규화합니다.
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
