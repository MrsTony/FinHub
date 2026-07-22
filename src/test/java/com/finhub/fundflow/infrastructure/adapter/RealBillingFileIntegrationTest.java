package com.finhub.fundflow.infrastructure.adapter;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实账单文件集成验证：用真实导出的支付宝/微信账单验证适配器。
 *
 * <p>文件路径写死为本地 D:/dev/文档/账单，文件缺失时测试自动跳过（不阻断 CI）。</p>
 */
public class RealBillingFileIntegrationTest {

    private static final String ALIPAY_REAL_CSV =
            "D:/dev/文档/账单/支付宝交易明细(20260409-20260709).csv";

    private static final String WECHAT_REAL_CSV =
            "D:/dev/文档/账单/微信支付账单流水文件(20260409-20260709)_20260709161443.csv";

    private final DataSourceAdapter alipayAdapter = new AlipayCSVAdapter();
    private final DataSourceAdapter wechatAdapter = new WechatCSVAdapter();

    @Test
    @DisplayName("真实支付宝账单应解析为 109 笔（89 笔支出 + 20 笔退款成功转收入，交易关闭 1 + 信用借还 2 跳过）")
    void shouldParseRealAlipayCsv() throws Exception {
        Path path = Paths.get(ALIPAY_REAL_CSV);
        assumeTrue(Files.exists(path), "真实支付宝账单文件不存在，跳过");

        try (InputStream in = new FileInputStream(path.toFile())) {
            List<RawRecord> records = alipayAdapter.adapt(in, "alipay_real.csv");

            System.out.println("=== 真实支付宝账单解析结果 ===");
            System.out.println("解析记录数: " + records.size());
            records.stream().limit(3).forEach(this::printRecord);

            // 文件声明"共112笔记录"：89笔支出 + 20笔退款成功(转收入) + 1笔交易关闭(跳过) + 2笔信用借还(跳过)
            assertThat(records).hasSize(109);
            // 支出 + 退款成功转收入
            assertThat(records).allMatch(r -> "支出".equals(r.direction()) || "收入".equals(r.direction()));
            assertThat(records).allMatch(r -> "ALIPAY".equals(r.sourceSystem()));
        }
    }

    @Test
    @DisplayName("真实微信账单应解析为 242 笔（收入17 + 支出225）")
    void shouldParseRealWechatCsv() throws Exception {
        Path path = Paths.get(WECHAT_REAL_CSV);
        assumeTrue(Files.exists(path), "真实微信账单文件不存在，跳过");

        try (InputStream in = new FileInputStream(path.toFile())) {
            List<RawRecord> records = wechatAdapter.adapt(in, "wechat_real.csv");

            System.out.println("=== 真实微信账单解析结果 ===");
            System.out.println("解析记录数: " + records.size());
            records.stream().limit(3).forEach(this::printRecord);

            // 文件声明"共242笔记录"：17笔收入 + 225笔支出
            assertThat(records).hasSize(242);
            assertThat(records).allMatch(r -> "WECHAT".equals(r.sourceSystem()));
            // 验证含千分位金额的大额记录（房租 6900.00）能正确解析
            assertThat(records).anyMatch(r -> r.amount().doubleValue() == 6900.00);
        }
    }

    private void printRecord(RawRecord r) {
        System.out.printf("externalId=%s, amount=%s, direction=%s, counterparty=%s%n",
                r.externalId(), r.amount(), r.direction(), r.counterparty());
    }
}
