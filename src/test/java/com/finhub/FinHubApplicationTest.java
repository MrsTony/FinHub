package com.finhub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用上下文加载测试契约。
 *
 * <p>隐含验证以下配置装配正确（任一缺失/错误则 contextLoads 失败）：
 * <ul>
 *   <li>{@code @MapperScan} 扫描 {@code com.finhub.**.infrastructure.repository.mapper}</li>
 *   <li>{@code application.yml} 的 {@code finhub.fingerprint.salt} / {@code finhub.encryption.key} 注入</li>
 *   <li>{@code CacheConfig} 的 Caffeine {@code Cache} Bean 存在</li>
 *   <li>{@code DataSourceAdapter} 经 {@code CompositeDataSourceAdapter}(@Primary) 唯一可注入</li>
 *   <li>远程 MySQL 连通 + Flyway baseline 建表</li>
 * </ul>
 *
 * <p>注意：远程 MySQL 不可达时此测试 <b>FAILED 而非跳过</b>（Context 加载阶段即连库）。
 * 本地若库不可达，用 {@code -Dtest=!FinHubApplicationTest} 排除。</p>
 */
@SpringBootTest
class FinHubApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("应用上下文应成功加载（隐含验证 MapperScan/yml/Cache/MySQL 装配）")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
