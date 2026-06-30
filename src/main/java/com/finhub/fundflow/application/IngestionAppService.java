package com.finhub.fundflow.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用层：导入编排服务。
 * 事务边界、用例编排、领域事件发布。
 *
 * <p>编排流程：CSV 解析 → 排重 → 分类 → 落库 → 发布事件</p>
 */
@Service
@Transactional
public class IngestionAppService {
    // TODO
}