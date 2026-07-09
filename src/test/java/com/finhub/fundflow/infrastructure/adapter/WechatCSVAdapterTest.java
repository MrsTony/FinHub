package com.finhub.fundflow.infrastructure.adapter;
import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 微信支付 CSV 适配器测试（基于真实导出格式契约）。
 *
 * <p>真实微信导出文件特征：UTF-8 with BOM、前置约 17 行元信息、表头 11 列、
 * 金额带 ¥ 且可能引号包裹千分位逗号（如 "¥6,900.00"）、商户单号可能为 /。</p>
 */
public class WechatCSVAdapterTest {
    private final DataSourceAdapter adapter = new WechatCSVAdapter();

    /** 真实表头（11 列） */
    private static final String HEADER =
            "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注";

    /** 构造含前置元信息 + BOM 的真实格式 CSV */
    private String realCsv(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("微信支付账单明细,,,,,,,,,,\n");
        sb.append("微信昵称：[测试用户],,,,,,,,,,\n");
        sb.append("导出时间：[2024-01-15 12:00:00],,,,,,,,,,\n");
        sb.append("共3笔记录,,,,,,,,,,\n");
        sb.append("----------------------微信支付账单明细列表--------------------,,,,,,,,,,\n");
        sb.append(HEADER).append("\n");
        for (String line : dataLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private InputStream stream(String content) {
        // 模拟真实文件：UTF-8 with BOM
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        return new ByteArrayInputStream(all);
    }

    private InputStream streamGBK(String content) {
        try {
            return new ByteArrayInputStream(content.getBytes("GBK"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("应解析真实微信格式：跳过前置元信息和 BOM")
    void shouldParseRealWechatFormat() {
        String csv = realCsv(
                "2024-01-15 12:30:45,商户消费,火山引擎,火山引擎订单,支出,¥8.46,广发银行信用卡(0000),支付成功,4200001234567890123456789012,商户订单001,/",
                "2024-01-15 18:20:10,扫二维码付款,滴滴出行,打车,支出,¥11.00,招商银行储蓄卡(1234),已转账,4200001234567890123456789013,商户订单002,/"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat_202401.csv");

        assertThat(records).hasSize(2);

        RawRecord r1 = records.get(0);
        assertThat(r1.externalId()).isEqualTo("4200001234567890123456789012");
        assertThat(r1.amount()).isEqualByComparingTo(new BigDecimal("8.46"));
        assertThat(r1.currency()).isEqualTo("CNY");
        assertThat(r1.direction()).isEqualTo("支出");
        assertThat(r1.counterparty()).isEqualTo("火山引擎");
        assertThat(r1.remark()).contains("火山引擎订单");
        assertThat(r1.sourceSystem()).isEqualTo("WECHAT");

        RawRecord r2 = records.get(1);
        assertThat(r2.externalId()).isEqualTo("4200001234567890123456789013");
        assertThat(r2.amount()).isEqualByComparingTo(new BigDecimal("11.00"));
    }

    @Test
    @DisplayName("应处理无 ¥ 符号的金额")
    void shouldHandleAmountWithoutYenSymbol() {
        String csv = realCsv(
                "2026-07-09 12:30:45,商户消费,美团外卖,午餐,支出,50.00,零钱,支付成功,4200001234567890123456789012,商户订单001,/"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("应处理引号包裹的千分位金额")
    void shouldHandleQuotedAmountWithThousandsSeparator() {
        String csv = realCsv(
                "2024-01-15 12:30:45,商户消费,测试商户,测试商品,支出,\"¥6,900.00\",广发银行信用卡(0000),支付成功,4200001234567890123456789099,商户订单099,/"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("6900.00"));
    }

    @Test
    @DisplayName("应处理收入交易")
    void shouldHandleIncomeTransaction() {
        String csv = realCsv(
                "2026-01-15 09:00:00,转账,张三,转账,收入,¥2000.00,零钱,已到账,4200001234567890123456789014,商户001,工资"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).direction()).isEqualTo("收入");
        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("应处理退款交易（方向仍为支出）")
    void shouldHandleRefundTransaction() {
        String csv = realCsv(
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐,支出,¥50.00,零钱,已退款,4200001234567890123456789012,商户订单001,退款-午餐"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).direction()).isEqualTo("支出");
    }

    @Test
    @DisplayName("应处理空商户单号（值为 / 占位符）")
    void shouldHandleEmptyMerchantOrderId() {
        String csv = realCsv(
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐,支出,50.00,零钱,支付成功,4200001234567890123456789012,/,午餐"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records.get(0).externalId()).isEqualTo("4200001234567890123456789012");
    }

    @Test
    @DisplayName("应拼接商品和备注")
    void shouldConcatenateProductAndRemark() {
        String csv = realCsv(
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐套餐,支出,50.00,零钱,支付成功,4200001234567890123456789012,商户订单001,加饮料"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records.get(0).remark()).contains("午餐套餐");
        assertThat(records.get(0).remark()).contains("加饮料");
    }

    @Test
    @DisplayName("应处理红包交易")
    void shouldHandleRedPacketTransaction() {
        String csv = realCsv(
                "2026-02-09 20:00:00,红包,李四,新年红包,支出,¥200.00,零钱,支付成功,4200001234567890123456789015,/,新年快乐"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).counterparty()).isEqualTo("李四");
        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("应处理转账交易")
    void shouldHandleTransferTransaction() {
        String csv = realCsv(
                "2026-01-15 10:00:00,转账,王五,转账,支出,¥1000.00,零钱,支付成功,4200001234567890123456789016,/,还款"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(records.get(0).direction()).isEqualTo("支出");
    }

    @Test
    @DisplayName("应处理 GBK 编码")
    void shouldHandleGbkEncoding() {
        // GBK 无 BOM，前置行字段数不匹配表头会被跳过
        String csv = realCsv(
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐,支出,50.00,零钱,支付成功,4200001234567890123456789012,商户订单001,午餐"
        );

        List<RawRecord> records = adapter.adapt(streamGBK(csv), "wechat_gbk.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).counterparty()).isEqualTo("美团外卖");
    }

    @Test
    @DisplayName("应跳过异常行并记录 WARN")
    void shouldSkipInvalidRowsAndLogWarning() {
        String csv = realCsv(
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐,支出,50.00,零钱,支付成功,4200001234567890123456789012,商户订单001,午餐",
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐,支出,INVALID_AMOUNT,零钱,支付成功,4200001234567890123456789013,商户订单002,午餐",
                "2026-01-15 12:30:45,商户消费,美团外卖,午餐,支出,60.00,零钱,支付成功,4200001234567890123456789014,商户订单003,午餐"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat.csv");

        assertThat(records).hasSize(2);
        assertThat(records.get(0).externalId()).isEqualTo("4200001234567890123456789012");
        assertThat(records.get(1).externalId()).isEqualTo("4200001234567890123456789014");
    }

    @Test
    @DisplayName("空文件（仅表头无数据）应返回空列表")
    void shouldReturnEmptyListForEmptyFile() {
        String csv = realCsv();

        List<RawRecord> records = adapter.adapt(stream(csv), "wechat_empty.csv");

        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("不支持支付宝格式应抛 UnsupportedOperationException")
    void shouldThrowForAlipayFormat() {
        String csv = """
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,
            2026-01-15 12:30:45,餐饮,美团外卖,mt@example.com,午餐,支出,¥50.00,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,,
            """;

        assertThatThrownBy(() -> adapter.adapt(stream(csv), "alipay.csv"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("暂不支持");
    }

    @Test
    @DisplayName("无法识别编码应抛 IllegalArgumentException")
    void shouldThrowForUnknownEncoding() {
        byte[] invalidBytes = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00};
        InputStream stream = new ByteArrayInputStream(invalidBytes);

        assertThatThrownBy(() -> adapter.adapt(stream, "wechat_invalid.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("编码");
    }

    @Test
    @DisplayName("null 输入应抛 NullPointerException")
    void shouldRejectNullInput() {
        assertThatThrownBy(() -> adapter.adapt(null, "wechat.csv"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null 文件名应抛 NullPointerException")
    void shouldRejectNullFilename() {
        assertThatThrownBy(() -> adapter.adapt(stream("test"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
