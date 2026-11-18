package com.msa.account.domain.auth.command.service;
import com.msa.account.domain.auth.command.dto.request.ChangePasswordRequest;
import java.util.UUID;
/** 이메일 복구와 구분되는 로그인 상태의 비밀번호 변경 계약입니다. */
public interface PasswordChangeService {
    void change(UUID accountId, ChangePasswordRequest request);
}
