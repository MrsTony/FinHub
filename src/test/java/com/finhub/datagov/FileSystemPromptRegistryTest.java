package com.finhub.datagov;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileSystemPromptRegistry} 加载与变量替换契约。纯单测，不连库。
 */
class FileSystemPromptRegistryTest {

    @TempDir
    Path promptsDir;

    private FileSystemPromptRegistry newRegistry() {
        return new FileSystemPromptRegistry(promptsDir.toString());
    }

    @Test
    @DisplayName("loadPrompt 应读取 prompts 目录下对应 .md 模板内容")
    void shouldLoadPromptByName() throws IOException {
        Files.writeString(promptsDir.resolve("classify-merchant.md"), "分类助手 {{merchant}}");
        assertThat(newRegistry().loadPrompt("classify-merchant"))
                .isEqualTo("分类助手 {{merchant}}");
    }

    @Test
    @DisplayName("loadPromptWithVariables 应替换 {{var}} 占位符")
    void shouldRenderVariables() throws IOException {
        Files.writeString(promptsDir.resolve("classify-merchant.md"), "商户：{{merchant}}，渠道：{{channel}}");
        String out = newRegistry().loadPromptWithVariables("classify-merchant",
                Map.of("merchant", "美团", "channel", "ALIPAY"));
        assertThat(out).isEqualTo("商户：美团，渠道：ALIPAY");
    }

    @Test
    @DisplayName("加载不存在的 prompt 应抛 IllegalArgumentException")
    void shouldThrowWhenPromptMissing() {
        assertThatThrownBy(() -> newRegistry().loadPrompt("not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-exist");
    }

    @Test
    @DisplayName("模板中未提供变量的占位符应保留原样（防误清空）")
    void shouldKeepPlaceholderWhenVariableAbsent() throws IOException {
        Files.writeString(promptsDir.resolve("p.md"), "你好 {{name}}，{{other}}");
        assertThat(newRegistry().loadPromptWithVariables("p", Map.of("name", "DGG")))
                .isEqualTo("你好 DGG，{{other}}");
    }
}
