package com.msa.account.global.config.membership;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 회원제 기능 플래그를 기존 설정 바인딩으로 제공합니다. */
@Configuration
@EnableConfigurationProperties(MembershipProperties.class)
public class MembershipConfig {}
