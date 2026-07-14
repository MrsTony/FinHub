package com.finhub.fundflow.infrastructure.adapter;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CompositeDataSourceAdapter} 测试契约（纯单测，Mock 两个 delegate）。
 *
 * <p>验证按文件名关键字路由分发：alipay/支付宝 -> AlipayCSVAdapter，
 * wechat/微信 -> WechatCSVAdapter，其余抛 IllegalArgumentException。
 * 透传不篡改原始流与文件名。</p>
 */
class CompositeDataSourceAdapterTest {

    private AlipayCSVAdapter alipayAdapter;
    private WechatCSVAdapter wechatAdapter;
    private CompositeDataSourceAdapter composite;

    @BeforeEach
    void setUp() {
        alipayAdapter = mock(AlipayCSVAdapter.class);
        wechatAdapter = mock(WechatCSVAdapter.class);
        composite = new CompositeDataSourceAdapter(alipayAdapter, wechatAdapter);
    }

    @Test
    @DisplayName("文件名含 alipay 应路由到 AlipayCSVAdapter")
    void shouldRouteToAlipayWhenFilenameContainsAlipay() {
        InputStream in = mock(InputStream.class);
        when(alipayAdapter.adapt(any(), any())).thenReturn(List.of());

        composite.adapt(in, "alipay_2024.csv");

        verify(alipayAdapter).adapt(eq(in), eq("alipay_2024.csv"));
        verifyNoInteractions(wechatAdapter);
    }

    @Test
    @DisplayName("文件名含中文「支付宝」应路由到 AlipayCSVAdapter")
    void shouldRouteToAlipayWhenFilenameContainsChineseKeyword() {
        InputStream in = mock(InputStream.class);
        when(alipayAdapter.adapt(any(), any())).thenReturn(List.of());

        composite.adapt(in, "支付宝账单.csv");

        verify(alipayAdapter).adapt(eq(in), eq("支付宝账单.csv"));
        verifyNoInteractions(wechatAdapter);
    }

    @Test
    @DisplayName("文件名含「微信」应路由到 WechatCSVAdapter")
    void shouldRouteToWechatWhenFilenameContainsWechat() {
        InputStream in = mock(InputStream.class);
        when(wechatAdapter.adapt(any(), any())).thenReturn(List.of());

        composite.adapt(in, "微信账单.csv");

        verify(wechatAdapter).adapt(eq(in), eq("微信账单.csv"));
        verifyNoInteractions(alipayAdapter);
    }

    @Test
    @DisplayName("无法识别的数据源应抛 IllegalArgumentException（含「无法识别数据源」）")
    void shouldThrowWhenSourceUnknown() {
        InputStream in = mock(InputStream.class);

        assertThatThrownBy(() -> composite.adapt(in, "bank.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别数据源");

        verifyNoInteractions(alipayAdapter, wechatAdapter);
    }

    @Test
    @DisplayName("文件名为 null 应抛 IllegalArgumentException")
    void shouldThrowWhenFilenameNull() {
        InputStream in = mock(InputStream.class);

        assertThatThrownBy(() -> composite.adapt(in, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(alipayAdapter, wechatAdapter);
    }

    @Test
    @DisplayName("路由后应透传原始 InputStream 与文件名，不篡改")
    void shouldPassThroughInputStreamAndFilename() {
        InputStream in = mock(InputStream.class);
        RawRecord sample = new RawRecord(
                "ext-1", new java.math.BigDecimal("10.00"), "CNY", "支出",
                "商户", "备注", java.time.LocalDateTime.now(), "ALIPAY");
        when(alipayAdapter.adapt(eq(in), eq("alipay_real.csv"))).thenReturn(List.of(sample));

        List<RawRecord> result = composite.adapt(in, "alipay_real.csv");

        assertThat(result).containsExactly(sample);
        verify(alipayAdapter).adapt(in, "alipay_real.csv");
    }
}
