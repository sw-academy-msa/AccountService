package com.msa.account.domain.account.command.dto.response;

import com.msa.account.domain.account.entity.Account;
import com.msa.account.domain.account.enums.AccountStatus;
import com.msa.account.domain.account.enums.Role;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

/**
 * 회원가입 결과를 전달합니다. 비밀번호 해시 등 인증용 내부 정보는 응답에 포함하지 않습니다.
 */
public record SignupResponse(
        UUID accountId,
        String email,
        String name,
        Instant emailVerifiedAt,
        AccountStatus status,
        List<Role> roles
) {
    // 엔티티에서 외부 공개가 필요한 필드만 선택하여 응답으로 변환합니다.
    public static SignupResponse from(Account account) {
        List<Role> sortedRoles = account.getRoles().stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();

        return new SignupResponse(
                account.getId(),
                account.getEmail(),
                account.getName(),
                account.getEmailVerifiedAt(),
                account.getStatus(),
                sortedRoles
        );
    }
}
