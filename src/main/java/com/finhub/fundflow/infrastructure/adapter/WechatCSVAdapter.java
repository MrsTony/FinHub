package com.finhub.fundflow.infrastructure.adapter;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import java.io.InputStream;
import java.util.List;

/** 微信支付 CSV 防腐层适配器 */
public class WechatCSVAdapter implements DataSourceAdapter {

    @Override
    public List<RawRecord> adapt(InputStream inputStream, String filename) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}