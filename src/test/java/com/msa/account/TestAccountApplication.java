package com.msa.account;

import com.msa.account.global.config.database.TestcontainersConfiguration;

import org.springframework.boot.SpringApplication;

/**
 * 테스트 컨테이너 설정을 포함하여 애플리케이션을 직접 실행할 때 사용하는 진입점입니다.
 */
public class TestAccountApplication {

	// Spring 애플리케이션 실행을 시작합니다.
	public static void main(String[] args) {
		SpringApplication.from(AccountApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
