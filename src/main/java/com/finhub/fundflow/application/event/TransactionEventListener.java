package com.finhub.fundflow.application.event;

import com.finhub.fundflow.domain.event.AnomalyDetectedEvent;
import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 交易领域事件监听器：消费分类/异常事件。MVP 仅日志记录。
 *
 * <p>同步 {@link EventListener}：事件发布即在当前事务内消费。未来若需"通知"语义
 * （事务提交后才触发、监听器失败不回滚导入），可改
 * {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)}（记 TODO）。</p>
 */
@Slf4j
@Component
public class TransactionEventListener {

    @EventListener
    public void onClassified(TransactionClassifiedEvent event) {
        log.info("交易分类完成: transactionId={}, category={}, source={}",
                event.transactionId(), event.category(), event.source());
    }

    @EventListener
    public void onAnomaly(AnomalyDetectedEvent event) {
        log.warn("异常交易标记: transactionId={}, score={}, reasonCode={}",
                event.transactionId(), event.score().score(), event.score().reasonCode());
    }
}
