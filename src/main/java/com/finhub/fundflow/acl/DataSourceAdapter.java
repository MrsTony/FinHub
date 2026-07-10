package com.finhub.fundflow.acl;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * 数据源适配器：防腐层接口。
 *
 * <p>将外部格式（CSV/Excel/JSON）转换为领域概念 RawRecord。
 * 核心域不认识 CSV，只认识 RawRecord。</p>
 *
 * <p>实现类：AlipayCSVAdapter（2024 新版）、WechatCSVAdapter（预留）、BankCSVAdapter（预留）。</p>
 */
public interface DataSourceAdapter {

    /**
     * 解析输入流为原始记录列表。
     *
     * @param inputStream 文件输入流（由应用层打开，适配器不管理资源）
     * @param filename    原始文件名（用于识别格式和编码，如 "alipay_20240101.csv"）
     * @return 原始记录列表（可能为空，不会为 null）
     * @throws IllegalArgumentException 格式不支持、编码识别失败、严重格式错误
     */
    List<RawRecord> adapt(InputStream inputStream, String filename);
}