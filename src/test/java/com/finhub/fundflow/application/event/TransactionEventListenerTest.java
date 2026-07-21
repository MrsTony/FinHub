package com.finhub.fundflow.application.event;

import com.finhub.fundflow.domain.event.AnomalyDetectedEvent;
import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import com.finhub.fundflow.domain.vo.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

/**
 * {@link TransactionEventListener} 装配与消费契约。
 *
 * <p>{@code @SpyBean} 包装真实监听器（日志真实输出），verify 确认 Spring 将事件路由到监听器。
 * 同步 {@code @EventListener}：publishEvent 返回即已消费，verify 立即可用。需远程 MySQL（@SpringBootTest 加载 Context）。</p>
 */
@Tag("integration")
@SpringBootTest
class TransactionEventListenerTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @SpyBean
    private TransactionEventListener listener;

    @Test
    @DisplayName("发布 TransactionClassifiedEvent 应被监听器消费")
    void shouldConsumeClassifiedEvent() {
        TransactionClassifiedEvent event = new TransactionClassifiedEvent(100L, Category.FOOD, "RULE");
        publisher.publishEvent(event);
        verify(listener).onClassified(event);
    }

    @Test
    @DisplayName("发布 AnomalyDetectedEvent 应被监听器消费")
    void shouldConsumeAnomalyEvent() {
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(200L,
                new AnomalyScore(new BigDecimal("0.9"), "AMOUNT_SPIKE"));
        publisher.publishEvent(event);
        verify(listener).onAnomaly(event);
    }
}
