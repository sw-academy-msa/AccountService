package com.msa.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Account Service의 시작점입니다. 하위 패키지의 컴포넌트와 설정을 검색해 Spring 애플리케이션을 실행합니다.
 */
@SpringBootApplication
public class AccountApplication {

	// Spring 애플리케이션 실행을 시작합니다.
	public static void main(String[] args) {
		SpringApplication.run(AccountApplication.class, args);
	}

}
