package com.msa.account.global.config.security;
import com.msa.account.domain.admin.command.service.AdminBootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
/** 명시적으로 활성화한 실행에서만 ADMIN을 지정합니다. 지정 후 활성화 옵션을 제거해야 합니다. */
@Configuration
@ConditionalOnProperty(prefix="account.admin",name="bootstrap-enabled",havingValue="true")
public class AdminBootstrapConfig {
    @Bean
    public ApplicationRunner initializeAdmin(AdminBootstrapService service,
            @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String email) {
        return args->service.grant(email);
    }
}
