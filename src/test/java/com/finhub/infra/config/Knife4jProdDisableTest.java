package com.finhub.infra.config;

import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 生产 profile（prod）下 Knife4j/springdoc 禁用契约。
 *
 * <p>验证：/v3/api-docs 在 prod 返回 404（springdoc.api-docs.enabled=false 致端点未注册）。
 * 注意：必须用 Basic Auth 认证请求，否则 Security 先返回 401，无法区分「端点存在(200)」与「端点已禁用(404)」。</p>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class Knife4jProdDisableTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("prod：/v3/api-docs 应返回 404（springdoc 已禁用，认证请求验证端点不存在）")
    void apiDocsShouldBeDisabledInProd() throws Exception {
        // 用 admin/dev-pass 认证绕过 Security 的 401，才能看到端点真实状态
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("admin", "dev-pass")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("prod：/api/** 仍需认证（未认证返回 401，安全基线不变）")
    void apiShouldStillRequireAuthInProd() throws Exception {
        mockMvc.perform(get("/api/transactions/import"))
                .andExpect(status().isUnauthorized());
    }
}