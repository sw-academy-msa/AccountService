package com.msa.account.global.config.withdrawal;

import org.springframework.context.annotation.*;

import java.net.http.HttpClient;
import java.time.Duration;

/** HTTP의 연결과 요청 시간을 제한하며 JWT를 다른 호스트로 redirect하지 않습니다. */
@Configuration
public class WithdrawalConfig {
    @Bean("withdrawalHttp")
    public HttpClient withdrawalHttp() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
