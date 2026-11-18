package com.msa.account;

import com.msa.account.global.config.database.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
/**
 * 실제 PostgreSQL 테스트 컨테이너와 함께 Spring 설정과 빈 구성이 정상적으로 시작되는지 확인합니다.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class AccountApplicationTests {

	@Test
	// 컨텍스트 로딩 자체가 실패하면 테스트도 실패하므로 별도 검증 구문이 필요하지 않습니다.
	void contextLoads() {
	}

}
