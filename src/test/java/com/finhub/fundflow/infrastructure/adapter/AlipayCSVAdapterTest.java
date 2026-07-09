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
 * 支付宝 CSV 适配器测试（基于真实导出格式契约）。
 *
 * <p>真实支付宝导出文件特征：GBK 编码、前置约 24 行元信息、表头 13 列含尾随逗号、
 * 交易订单号/商家订单号字段含 Tab、"不计收支"方向值、金额无 ¥ 符号。</p>
 */
public class AlipayCSVAdapterTest {
    private final DataSourceAdapter adapter = new AlipayCSVAdapter();

    /** 真实表头（13 列，末尾尾随逗号） */
    private static final String HEADER =
            "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,";

    /** 构造含前置元信息的真实格式 CSV */
    private String realCsv(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------\n");
        sb.append("导出信息：\n");
        sb.append("姓名：测试用户\n");
        sb.append("------------------------支付宝电子客户回单------------------------\n");
        sb.append(HEADER).append("\n");
        for (String line : dataLines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream streamGBK(String content) {
        try {
            return new ByteArrayInputStream(content.getBytes("GBK"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("应解析真实支付宝格式：跳过前置元信息、不计收支，保留支出记录")
    void shouldParseRealAlipayFormat() {
        // 订单号/商家订单号含 Tab；含一条"不计收支"应被跳过
        String csv = realCsv(
                "2024-01-15 12:30:45,餐饮美食,美团外卖,test@example.com,午餐,支出,17.10,招商银行,交易成功,202401011200000000000001\t,商户订单001\t,,",
                "2024-01-15 18:20:10,交通出行,滴滴出行,test2@example.com,打车,支出,1.00,招商银行,交易成功,202401011200000000000002\t,商户订单002\t,,",
                "2024-06-30 08:42:12,文化休闲,测试商户,/,测试VIP,不计收支,328.00,,交易关闭,202401011200000000000003\t,商户订单003\t,,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay_202401.csv");

        assertThat(records).hasSize(2);

        RawRecord r1 = records.get(0);
        assertThat(r1.externalId()).isEqualTo("202401011200000000000001");
        assertThat(r1.amount()).isEqualByComparingTo(new BigDecimal("17.10"));
        assertThat(r1.currency()).isEqualTo("CNY");
        assertThat(r1.direction()).isEqualTo("支出");
        assertThat(r1.counterparty()).isEqualTo("美团外卖");
        assertThat(r1.remark()).contains("午餐");
        assertThat(r1.sourceSystem()).isEqualTo("ALIPAY");

        RawRecord r2 = records.get(1);
        assertThat(r2.externalId()).isEqualTo("202401011200000000000002");
        assertThat(r2.amount()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("应处理 GBK 编码文件")
    void shouldHandleGbkEncoding() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,50.00,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,,"
        );

        List<RawRecord> records = adapter.adapt(streamGBK(csv), "alipay_gbk.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).counterparty()).isEqualTo("美团外卖");
    }

    @Test
    @DisplayName("应去除金额中的 ¥ 符号")
    void shouldRemoveYenSymbolFromAmount() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,¥100.50,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay.csv");

        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("100.50"));
    }

    @Test
    @DisplayName("应处理 +/- 前缀金额（取绝对值）")
    void shouldHandlePlusMinusPrefix() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,+50.00,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,,",
                "2026-07-09 12:30:45,工资,公司,company@example.com,工资,收入,-5000.00,招商银行,交易成功,2024011512304512345679\t,商户订单002\t,,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay.csv");

        assertThat(records).hasSize(2);
        assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(records.get(1).amount()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("应透传方向为 收入/支出")
    void shouldPassThroughDirection() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,50.00,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,,",
                "2026-07-09 12:30:45,工资,公司,company@example.com,工资,收入,5000.00,招商银行,交易成功,2024011512304512345679\t,商户订单002\t,,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay.csv");

        assertThat(records.get(0).direction()).isEqualTo("支出");
        assertThat(records.get(1).direction()).isEqualTo("收入");
    }

    @Test
    @DisplayName("应拼接备注和商品说明")
    void shouldConcatenateRemarkAndProductDescription() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐套餐,支出,50.00,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,加饮料,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay.csv");

        assertThat(records.get(0).remark()).contains("午餐套餐");
        assertThat(records.get(0).remark()).contains("加饮料");
    }

    @Test
    @DisplayName("应处理 externalId 为空的记录")
    void shouldHandleEmptyExternalId() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,50.00,招商银行,交易成功,,商户订单001\t,,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay.csv");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).externalId()).isNull();
    }

    @Test
    @DisplayName("应跳过异常行并记录 WARN")
    void shouldSkipInvalidRowsAndLogWarning() {
        String csv = realCsv(
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,50.00,招商银行,交易成功,2024011512304512345678\t,商户订单001\t,,",
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,INVALID_AMOUNT,招商银行,交易成功,2024011512304512345679\t,商户订单002\t,,",
                "2026-07-09 12:30:45,餐饮美食,美团外卖,mt@example.com,午餐,支出,60.00,招商银行,交易成功,2024011512304512345680\t,商户订单003\t,,"
        );

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay.csv");

        assertThat(records).hasSize(2);
        assertThat(records.get(0).externalId()).isEqualTo("2024011512304512345678");
        assertThat(records.get(1).externalId()).isEqualTo("2024011512304512345680");
    }

    @Test
    @DisplayName("空文件（仅表头无数据）应返回空列表")
    void shouldReturnEmptyListForEmptyFile() {
        String csv = realCsv();

        List<RawRecord> records = adapter.adapt(stream(csv), "alipay_empty.csv");

        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("不支持微信格式应抛 UnsupportedOperationException")
    void shouldThrowForUnsupportedFormat() {
        String csv = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-01-15 12:30:45,商户消费,美团外卖,午餐,50.00,支出,零钱,支付成功,1234567890,商户001,午餐
            """;

        assertThatThrownBy(() -> adapter.adapt(stream(csv), "wechat.csv"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("暂不支持");
    }

    @Test
    @DisplayName("无法识别编码应抛 IllegalArgumentException")
    void shouldThrowForUnknownEncoding() {
        byte[] invalidBytes = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00};
        InputStream stream = new ByteArrayInputStream(invalidBytes);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.adapt(stream, "alipay_invalid.bin"))
                .withMessageContaining("编码");
    }

    @Test
    @DisplayName("null 输入应抛 NullPointerException")
    void shouldRejectNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.adapt(null, "alipay.csv"));
    }

    @Test
    @DisplayName("null 文件名应抛 NullPointerException")
    void shouldRejectNullFilename() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.adapt(stream("test"), null));
    }

}
