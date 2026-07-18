package com.finhub.fundflow.interfaces;

import com.finhub.fundflow.application.IngestionAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link IngestionController} 测试契约（{@code @SpringBootTest} + MockMvc + {@code @MockBean} AppService + Basic Auth）。
 *
 * <p>采用全上下文而非 {@code @WebMvcTest}：因 {@code @MapperScan} 在 Web 切片下缺少 SqlSessionFactory 而无法装配。
 * Basic Auth 复用 {@code SecurityConfig}（admin/dev-pass，未注入密码时的兜底值）。</p>
 *
 * <p>验证：200 上传成功返回计数、400 文件空/文件名空/AppService 抛 IAE、500 意外异常。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngestionControllerTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "dev-pass"; // SecurityConfig 未注入密码时的兜底值

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionAppService ingestionAppService;

    @Test
    @DisplayName("上传有效 CSV 应返回 200 + JSON 计数")
    void shouldReturn200WithCountsWhenUploadValidCsv() throws Exception {
        MockMultipartFile file = csvFile("alipay.csv", "交易时间,交易分类,交易对方");
        when(ingestionAppService.importFile(any(), anyString()))
                .thenReturn(new IngestionAppService.ImportResult(2, 0, 0));

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    @DisplayName("空文件应返回 400")
    void shouldReturn400WhenFileIsEmpty() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "alipay.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/transactions/import").file(empty).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("文件名为 null 应返回 400")
    void shouldReturn400WhenFilenameNull() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", null, "text/csv", "content".getBytes());

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AppService 抛 IllegalArgumentException 应返回 400 + 错误消息")
    void shouldReturn400WhenAppServiceThrowsIllegalArgument() throws Exception {
        MockMultipartFile file = csvFile("alipay.csv", "content");
        when(ingestionAppService.importFile(any(), anyString()))
                .thenThrow(new IllegalArgumentException("无法识别数据源: bad.csv"));

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("无法识别数据源: bad.csv"));
    }

    @Test
    @DisplayName("AppService 抛 RuntimeException 应返回 500")
    void shouldReturn500OnUnexpectedException() throws Exception {
        MockMultipartFile file = csvFile("alipay.csv", "content");
        when(ingestionAppService.importFile(any(), anyString()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isInternalServerError());
    }

    private MockMultipartFile csvFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes());
    }
}
