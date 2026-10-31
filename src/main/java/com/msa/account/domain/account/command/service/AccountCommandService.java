package com.msa.account.domain.account.command.service;

import com.msa.account.domain.account.command.dto.request.SignupRequest;
import com.msa.account.domain.account.command.dto.response.SignupResponse;

/**
 * 회원 변경 기능의 계약입니다. 컨트롤러는 이 인터페이스에 의존하고 구현체가 저장과 암호화를 처리합니다.
 */
public interface AccountCommandService {

    SignupResponse signup(SignupRequest request);
}
