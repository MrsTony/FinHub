package com.finhub.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OpenAPI 文档元数据配置（仅非 prod）。
 *
 * <p>提供标题/描述/版本与 BasicAuth securityScheme，让 Knife4j「Authorize」
 * 可配置 Basic Auth（admin/dev-pass），从而调试 /api/** 真实接口。
 * prod 下不创建此 bean，配合 springdoc 禁用从源头杜绝文档生成。</p>
 */
@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    public OpenAPI finHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinHub API")
                        .description("个人资金数据治理中台 - 接口文档")
                        .version("0.1.0"))
                .components(new Components()
                        .addSecuritySchemes("BasicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")));
    }
}