package com.finhub.infra.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CacheConfig} 配置装配测试契约（{@code @SpringBootTest}）。
 *
 * <p>验证去重缓存 Bean 存在、类型正确、且为可用的 Caffeine 实现（put/get 生效）。
 * 缺此 Bean 应用无法启动（{@code DeduplicationServiceImpl} 构造器注入 Cache）。</p>
 */
@SpringBootTest
class CacheConfigTest {

    @Autowired
    private Cache<String, Boolean> dedupCache;

    @Test
    @DisplayName("应注入非空的去重缓存 Bean，类型为 Cache<String,Boolean>")
    void shouldProvideDedupCacheBean() {
        assertThat(dedupCache).isNotNull();
    }

    @Test
    @DisplayName("缓存应为可用的 Caffeine 实现：put 后 getIfPresent 命中")
    void shouldHaveCaffeineSpec() {
        dedupCache.put("dedup-key-001", Boolean.TRUE);

        assertThat(dedupCache.getIfPresent("dedup-key-001")).isTrue();
    }
}
