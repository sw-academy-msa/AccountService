package com.msa.account.global.config.scheduling;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Account 내부 스케줄링을 활성화합니다. 별도 배포 서비스는 필요하지 않습니다. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuthCleanupProperties.class)
public class AuthCleanupConfig { }
