package com.msa.account.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서의 제목, 버전, 공통 JWT 인증 표시를 설정합니다. 실제 토큰 검증은 보안 설정의 역할입니다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    // 문서 공통 정보를 등록하고 Swagger의 Authorize 입력창에 Bearer JWT 방식을 안내합니다.
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("STOCKDROP Account Service API")
                        .description("회원, 인증, 권한, 판매자 신청을 담당하는 Account Service API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }
}
