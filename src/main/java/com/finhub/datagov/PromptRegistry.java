package com.finhub.datagov;

import java.util.Map;

/**
 * Prompt 注册表：从 DVC 管理的文件系统加载 Prompt 模板。
 * 禁止硬编码 Prompt 字符串到代码中。
 */
public interface PromptRegistry {

    String loadPrompt(String promptName);

    String loadPromptWithVariables(String promptName, Map<String, String> variables);
}