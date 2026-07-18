package com.finhub.infra.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StartupBanner 面板内容契约（纯单测，测 buildPanel 字符串构建）。
 *
 * <p>非 prod：含本地+局域网 /doc.html 链接、API 端点、Basic Auth 凭据、应用名。
 * prod：含「Knife4j 已禁用」，不含链接与凭据。</p>
 */
class StartupBannerTest {

    @Test
    @DisplayName("非 prod：面板应含本地/局域网 doc.html 链接、API 端点、凭据、应用名")
    void shouldContainLinksAndCredentialsWhenNotProd() {
        String panel = StartupBanner.buildPanel(
                false, "FinHub", "0.1.0", 8080, "", "192.168.1.100");

        assertThat(panel)
                .contains("FinHub")
                .contains("0.1.0")
                .contains("http://localhost:8080/doc.html")
                .contains("http://192.168.1.100:8080/doc.html")
                .contains("/api/transactions/import")
                .contains("admin/dev-pass");
    }

    @Test
    @DisplayName("非 prod：contextPath 非空时应拼到链接路径前")
    void shouldIncludeContextPathInLinks() {
        String panel = StartupBanner.buildPanel(
                false, "FinHub", "0.1.0", 8080, "/finhub", "192.168.1.100");

        assertThat(panel).contains("http://localhost:8080/finhub/doc.html");
    }

    @Test
    @DisplayName("prod：面板应含「Knife4j 已禁用」，不含链接与凭据")
    void shouldNotContainLinksAndCredentialsWhenProd() {
        String panel = StartupBanner.buildPanel(
                true, "FinHub", "0.1.0", 8080, "", "192.168.1.100");

        assertThat(panel)
                .contains("Knife4j 已禁用")
                .doesNotContain("/doc.html")
                .doesNotContain("admin/dev-pass");
    }

    @Test
    @DisplayName("局域网 IP 为 N/A 时面板应包含 N/A，不报错")
    void shouldHandleLanIpNA() {
        String panel = StartupBanner.buildPanel(
                false, "FinHub", "0.1.0", 8080, "", "N/A");

        assertThat(panel).contains("N/A");
    }
}