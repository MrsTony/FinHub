package com.finhub.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenApiConfig 装配契约（默认 profile，非 prod）。
 *
 * <p>验证：OpenAPI bean 存在且含 BasicAuth securityScheme，
 * 供 Knife4j「Authorize」配置 Basic Auth 调试 /api/** 真实接口。</p>
 */
@Tag("integration")
@SpringBootTest
class OpenApiConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("非 prod：应提供含 BasicAuth securityScheme 的 OpenAPI bean")
    void shouldProvideOpenApiWithBasicAuthScheme() {
        OpenAPI openAPI = applicationContext.getBean(OpenAPI.class);

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKey("BasicAuth");
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("FinHub API");
    }
}