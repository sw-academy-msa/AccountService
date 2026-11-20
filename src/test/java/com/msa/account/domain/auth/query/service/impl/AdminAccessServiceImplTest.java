package com.msa.account.domain.auth.query.service.impl;

import com.msa.account.domain.account.enums.*;
import com.msa.account.domain.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 관리자 자격 조회가 잘못된 식별자와 DB 장애를 허용으로 바꾸지 않는지 확인합니다. */
class AdminAccessServiceImplTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AdminAccessServiceImpl service = new AdminAccessServiceImpl(accounts);

    @Test
    void rejectsMalformedSubjectWithoutQuery() {
        assertThat(service.isCurrentAdmin(null)).isFalse();
        assertThat(service.isCurrentAdmin("not-a-uuid")).isFalse();
        verifyNoInteractions(accounts);
    }

    @Test
    void neverFallsBackToTokenRoleDuringDbFailure() {
        UUID id = UUID.randomUUID();
        when(accounts.hasCurrentRole(id, AccountStatus.ACTIVE, Role.ADMIN))
                .thenThrow(new DataAccessResourceFailureException("fixture-only"));
        assertThatThrownBy(() -> service.isCurrentAdmin(id.toString()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
