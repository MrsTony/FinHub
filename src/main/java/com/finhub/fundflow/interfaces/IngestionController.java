package com.finhub.fundflow.interfaces;

import com.finhub.fundflow.application.IngestionAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 交易导入 REST 入口。
 *
 * <p>接口层职责：接收 HTTP 请求、参数校验、委托应用服务编排、返回结果。
 * 不包含业务规则（全部在应用/领域层）。Basic Auth 由 {@code SecurityConfig} 统一保护。</p>
 */
@Slf4j
@Tag(name = "交易导入", description = "资金流水文件导入接口")
@RestController
@RequestMapping("/api/transactions")
public class IngestionController {

    private final IngestionAppService appService;

    public IngestionController(IngestionAppService appService) {
        this.appService = appService;
    }

    /**
     * 上传 CSV 账单文件并导入。
     *
     * @param file 账单文件（multipart/form-data，字段名 file）
     * @return 导入结果（成功/跳过/失败计数）
     * @throws IllegalArgumentException 文件为空或文件名缺失（映射 400）
     * @throws IOException 读取文件流失败（映射 500）
     */
    @Operation(summary = "导入交易文件", description = "上传 CSV 账单文件，解析、排重、分类、异常检测后导入资金流水")
    @PostMapping("/import")
    public IngestionAppService.ImportResult importFile(
            @Parameter(description = "账单文件（CSV，multipart）", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        log.info("收到导入请求: filename={}, size={}", filename, file.getSize());
        return appService.importFile(file.getInputStream(), filename);
    }
}
