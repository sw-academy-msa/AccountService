package com.msa.account.global.config.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 이메일 인증에 사용하는 시간·횟수·발신자 정책을 등록합니다. */
@Configuration
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig { }
