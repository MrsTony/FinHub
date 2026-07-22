package com.finhub.fundflow.interfaces;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导入流水线端到端集成测试。
 *
 * <p>验证完整闭环：Controller -> CompositeDataSourceAdapter 路由 -> CSV 适配器 ->
 * IngestionAppService 编排（加密/指纹/去重/分类/异常检测/持久化/事件）-> 远程 MySQL。
 * 使用合成 CSV（带运行期唯一 externalId）避免 DB 历史数据与 Caffeine 缓存的跨用例污染。</p>
 *
 * <p>{@code @Transactional} 自动回滚，不污染远程库。{@code @Tag("integration")} 标记，
 * DB 不可达时 {@code assumeTrue} 跳过整类。</p>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class IngestionEndToEndTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "dev-pass";

    /** 真实账单文件路径（存在则跑真实文件 E2E，否则跳过） */
    private static final String REAL_ALIPAY_CSV =
            "D:/dev/文档/账单/支付宝交易明细(20260409-20260709).csv";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DataSource dataSource;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.finhub.fundflow.application.event.TransactionEventListener eventListener;

    @BeforeAll
    void probeDatabase() throws Exception {
        boolean reachable;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            reachable = rs.next();
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "远程 MySQL 不可达，跳过 E2E 集成测试");
    }

    @Test
    @DisplayName("合成支付宝 CSV 端到端：导入 2 笔，分类为 FOOD/TRANSPORT，可按 externalId 查回")
    void shouldImportSyntheticAlipayCsvEndToEnd() throws Exception {
        String nano = String.valueOf(System.nanoTime());
        String extId1 = "E2EALI" + nano + "1";
        String extId2 = "E2EALI" + nano + "2";
        String csv = alipayCsv(
                "2024-01-15 12:30:45,餐饮美食,美团外卖,test@example.com,午餐,支出,17.10,招商银行,交易成功," + extId1 + "\t,商户订单001\t,,",
                "2024-01-15 18:20:10,交通出行,滴滴出行,test2@example.com,打车,支出,1.00,招商银行,交易成功," + extId2 + "\t,商户订单002\t,,");

        MockMultipartFile file = new MockMultipartFile("file", "alipay_e2e.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(0));

        // 按 externalId 查回，验证全链路落库 + 分类闭环（美团 -> FOOD）
        Transaction loaded = transactionRepository.findByExternalId(extId1)
                .orElseThrow(() -> new AssertionError("导入后应能按 externalId 查回: " + extId1));
        assertThat(loaded.getCategory()).isEqualTo(com.finhub.fundflow.domain.vo.Category.FOOD);
        assertThat(loaded.getSourceSystem()).isEqualTo("ALIPAY");

        Transaction loaded2 = transactionRepository.findByExternalId(extId2).orElseThrow();
        assertThat(loaded2.getCategory()).isEqualTo(com.finhub.fundflow.domain.vo.Category.TRANSPORT);
    }

    @Test
    @DisplayName("合成微信 CSV 端到端：导入 1 笔，分类为 FOOD，可查回")
    void shouldImportSyntheticWechatCsvEndToEnd() throws Exception {
        String nano = String.valueOf(System.nanoTime());
        String extId = "E2EWX" + nano;
        String csv = wechatCsv(
                "2024-01-15 12:30:45,商户消费,美团外卖,美团订单,支出,¥17.10,招商银行,支付成功," + extId + ",商户订单001,/");

        MockMultipartFile file = new MockMultipartFile("file", "wechat_e2e.csv", "text/csv", withBom(csv));

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        Transaction loaded = transactionRepository.findByExternalId(extId)
                .orElseThrow(() -> new AssertionError("导入后应能按 externalId 查回: " + extId));
        assertThat(loaded.getCategory()).isEqualTo(com.finhub.fundflow.domain.vo.Category.FOOD);
        assertThat(loaded.getSourceSystem()).isEqualTo("WECHAT");
    }

    @Test
    @DisplayName("真实支付宝账单端到端：返回 200 且流水被处理（imported 或 skipped > 0）")
    void shouldImportRealAlipayCsvEndToEnd() throws Exception {
        Path path = Paths.get(REAL_ALIPAY_CSV);
        assumeTrue(Files.exists(path), "真实支付宝账单文件不存在，跳过");

        byte[] content = Files.readAllBytes(path);
        MockMultipartFile file = new MockMultipartFile("file", "alipay_real.csv", "text/csv", content);

        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                // 真实文件 89 笔：DB 干净时 imported>0；若已有历史数据则 skipped>0（去重）。任一>0 即证明流水被处理
                .andExpect(result -> {
                    int imported = readField(result.getResponse().getContentAsString(), "imported");
                    int skipped = readField(result.getResponse().getContentAsString(), "skipped");
                    if (imported <= 0 && skipped <= 0) {
                        throw new AssertionError("期望 imported 或 skipped > 0，实际 imported=" + imported + " skipped=" + skipped);
                    }
                });
    }

    @Test
    @DisplayName("端到端导入后监听器应收到带非 null transactionId 的分类事件（id 回填 + 事件丰富化闭环）")
    void shouldPublishClassifiedEventWithRealTransactionId() throws Exception {
        String nano = String.valueOf(System.nanoTime());
        String extId = "E2EEVT" + nano;
        String csv = alipayCsv(
                "2024-01-15 12:30:45,餐饮美食,美团外卖,test@example.com,午餐,支出,17.10,招商银行,交易成功," + extId + "\t,商户订单001\t,,");

        MockMultipartFile file = new MockMultipartFile("file", "alipay_evt.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        // 监听器应收到分类事件，且 transactionId 非空（id 已被 saveBatch 回填并丰富进事件）
        org.mockito.ArgumentCaptor<com.finhub.fundflow.domain.event.TransactionClassifiedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(com.finhub.fundflow.domain.event.TransactionClassifiedEvent.class);
        org.mockito.Mockito.verify(eventListener).onClassified(captor.capture());
        assertThat(captor.getValue().transactionId()).isNotNull();
        assertThat(captor.getValue().transactionId()).isPositive();
        assertThat(captor.getValue().category()).isEqualTo(com.finhub.fundflow.domain.vo.Category.FOOD);
    }

    // =========================================================================
    // 辅助：合成 CSV 构造（格式与 AlipayCSVAdapterTest/WechatCSVAdapterTest 对齐）
    // =========================================================================

    private static final String ALIPAY_HEADER =
            "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,";

    private String alipayCsv(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------\n");
        sb.append("导出信息：\n");
        sb.append("姓名：测试用户\n");
        sb.append("------------------------支付宝电子客户回单------------------------\n");
        sb.append(ALIPAY_HEADER).append("\n");
        for (String line : dataLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static final String WECHAT_HEADER =
            "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注";

    private String wechatCsv(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("微信支付账单明细,,,,,,,,,,\n");
        sb.append("微信昵称：[测试用户],,,,,,,,,,\n");
        sb.append("导出时间：[2024-01-15 12:00:00],,,,,,,,,,\n");
        sb.append("共1笔记录,,,,,,,,,,\n");
        sb.append("----------------------微信支付账单明细列表--------------------,,,,,,,,,,\n");
        sb.append(WECHAT_HEADER).append("\n");
        for (String line : dataLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /** 微信真实文件带 UTF-8 BOM，合成文件也加上以贴近真实 */
    private byte[] withBom(String content) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        return all;
    }

    /** 从 JSON 响应中粗略提取整型字段值（避免引入 JsonPath 依赖到断言 lambda） */
    private int readField(String json, String field) {
        String token = "\"" + field + "\":";
        int idx = json.indexOf(token);
        if (idx < 0) {
            return -1;
        }
        int start = idx + token.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end));
    }
}
