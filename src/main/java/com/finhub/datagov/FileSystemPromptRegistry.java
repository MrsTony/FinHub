package com.finhub.datagov;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * {@link PromptRegistry} 的文件系统实现：从 DVC 管理的 {@code prompts/} 目录加载模板。
 *
 * <p>模板文件名 = {@code <promptName>.md}；支持 {@code {{var}}} 占位符替换，
 * 未提供变量的占位符保留原样。禁止把 Prompt 硬编码进代码——新增/修改 Prompt 只动
 * {@code prompts/} 目录并 {@code dvc add}。</p>
 */
public class FileSystemPromptRegistry implements PromptRegistry {

    private final Path promptsDir;

    public FileSystemPromptRegistry(String promptsDir) {
        this.promptsDir = Path.of(promptsDir);
    }

    @Override
    public String loadPrompt(String promptName) {
        Path file = promptsDir.resolve(promptName + ".md");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Prompt 不存在: " + promptName);
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 Prompt 失败: " + promptName, e);
        }
    }

    @Override
    public String loadPromptWithVariables(String promptName, Map<String, String> variables) {
        String template = loadPrompt(promptName);
        if (variables == null) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return out;
    }
}
