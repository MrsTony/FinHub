package com.finhub.fundflow.infrastructure.adapter;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import java.io.InputStream;
import java.util.List;

/**
 * 支付宝 CSV 防腐层适配器（2024 新版格式）。
 * 核心域不认识 CSV，只认识 RawRecord。
 */
public class AlipayCSVAdapter implements DataSourceAdapter {

    @Override
    public List<RawRecord> adapt(InputStream inputStream, String filename) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}