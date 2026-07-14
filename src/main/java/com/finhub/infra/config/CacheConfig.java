package com.finhub.infra.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine 缓存配置。
 *
 * <p>当前提供去重缓存 Bean，供 {@code DeduplicationServiceImpl} 构造器注入。
 * 缺此 Bean 应用无法启动（NoUniqueBeanDefinition / 缺失依赖）。</p>
 *
 * <p>注意：此 Bean 的测试契约在 Day5 第三步补全（CacheConfigTest）。</p>
 */
@Configuration
public class CacheConfig {

    /**
     * 去重缓存：external_id / fingerprint -> true，降低 DB 查询压力。
     * 非权威，真正唯一性由 DB 唯一约束兜底。
     */
    @Bean
    public Cache<String, Boolean> dedupCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build();
    }
}
