package com.finhub.infra.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 非 prod 环境 Knife4j 文档访问契约（默认 profile）。
 *
 * <p>验证：/doc.html 与 /api/** 均免认证可访问（非 prod 放行）。</p>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class Knife4jDevAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("非 prod：/doc.html 应免认证可访问（状态码 < 400）")
    void docHtmlShouldBeAccessibleWithoutAuthInDev() throws Exception {
        mockMvc.perform(get("/doc.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("/doc.html 在非 prod 应免认证可访问（permitAll），实际状态: " + status)
                            .isLessThan(400);
                });
    }

    @Test
    @DisplayName("非 prod：/api/** 免认证可达（空文件 POST 返回 400 而非 401，证明 Security 放行）")
    void apiShouldBeAccessibleWithoutAuthInDev() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "alipay.csv", "text/csv", new byte[0]);
        mockMvc.perform(multipart("/api/transactions/import").file(empty))
                .andExpect(status().isBadRequest());  // 空文件 -> 控制器 IAE -> 400，未走 401
    }
}