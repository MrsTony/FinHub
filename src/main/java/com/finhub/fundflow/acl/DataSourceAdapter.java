package com.finhub.fundflow.acl;

import java.io.InputStream;
import java.util.List;

/**
 * 防腐层：数据源适配器接口。
 * 核心域只认识 RawRecord，不认识 CSV/PDF/银行格式。
 * 新增渠道只需加一个 Adapter 实现，符合开闭原则。
 */
public interface DataSourceAdapter {

    List<RawRecord> adapt(InputStream inputStream, String filename);
}