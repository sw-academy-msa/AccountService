package com.msa.account.domain.account.repository;

import com.msa.account.domain.account.entity.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 회원 저장과 검색을 담당합니다. Spring Data JPA가 인터페이스의 구현체를 생성합니다.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // 정규화한 이메일이 이미 등록되어 있는지 확인합니다.
    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    // 회원과 권한을 함께 조회하여 트랜잭션 밖에서 권한을 읽을 때 지연 로딩 문제를 방지합니다.
    Optional<Account> findByEmail(String email);
}
