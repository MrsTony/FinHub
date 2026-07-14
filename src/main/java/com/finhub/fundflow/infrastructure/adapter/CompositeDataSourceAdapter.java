package com.finhub.fundflow.infrastructure.adapter;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * 复合数据源适配器：按文件名关键字路由到具体的支付宝/微信适配器。
 *
 * <h3>存在意义</h3>
 * <p>应用层 {@code IngestionAppService} 仅注入单个 {@link DataSourceAdapter}，
 * 但 {@link AlipayCSVAdapter} 与 {@link WechatCSVAdapter} 都是 Spring Bean，
 * 直接注入会触发 {@code NoUniqueBeanDefinitionException}。本类作为
 * {@code @Primary} 主 Bean，承担路由分发职责，对外暴露单一适配器入口。</p>
 *
 * <h3>路由规则（按文件名关键字，大小写不敏感）</h3>
 * <ul>
 *   <li>含 {@code alipay} 或 {@code 支付宝} -> {@link AlipayCSVAdapter}</li>
 *   <li>含 {@code wechat} 或 {@code 微信} -> {@link WechatCSVAdapter}</li>
 *   <li>其余 -> {@link IllegalArgumentException}</li>
 * </ul>
 */
@Slf4j
@Primary
@Service
public class CompositeDataSourceAdapter implements DataSourceAdapter {

    private final AlipayCSVAdapter alipayAdapter;
    private final WechatCSVAdapter wechatAdapter;

    public CompositeDataSourceAdapter(AlipayCSVAdapter alipayAdapter, WechatCSVAdapter wechatAdapter) {
        this.alipayAdapter = alipayAdapter;
        this.wechatAdapter = wechatAdapter;
    }

    @Override
    public List<RawRecord> adapt(InputStream inputStream, String filename) {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        DataSourceAdapter delegate;

        if (lower.contains("alipay") || filename.contains("支付宝")) {
            delegate = alipayAdapter;
        } else if (lower.contains("wechat") || filename.contains("微信")) {
            delegate = wechatAdapter;
        } else {
            throw new IllegalArgumentException("无法识别数据源: " + filename);
        }

        log.info("数据源路由: filename={} -> {}", filename, delegate.getClass().getSimpleName());
        return delegate.adapt(inputStream, filename);
    }
}
