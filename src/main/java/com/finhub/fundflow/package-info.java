/**
 * 资金流水上下文（fundflow）— 核心域
 *
 * <h3>职责边界</h3>
 * <p>守护 Transaction 聚合根的不变量（金额精度、排重指纹、分类一致性），
 * 所有资金数据的唯一写入入口。</p>
 *
 * <h3>DDD 分层</h3>
 * <ul>
 *   <li>{@code domain.aggregate} — Transaction 聚合根</li>
 *   <li>{@code domain.vo} — Money, Fingerprint, EncryptedString 等值对象</li>
 *   <li>{@code domain.service} — DeduplicationService, FingerprintGenerator 等领域服务</li>
 *   <li>{@code domain.event} — 领域事件</li>
 *   <li>{@code domain.repository} — 仓库接口（领域层定义）</li>
 *   <li>{@code application} — IngestionAppService, QueryAppService（用例编排）</li>
 *   <li>{@code acl} — DataSourceAdapter 防腐层接口</li>
 *   <li>{@code infrastructure} — 仓库实现、CSV 适配器、缓存</li>
 * </ul>
 *
 * <h3>边界规则</h3>
 * <ul>
 *   <li>禁止直接依赖本地知识库上下文</li>
 *   <li>领域层禁止 Spring 注解（除领域事件发布器）</li>
 *   <li>核心域只认识 RawRecord，不认识 CSV/LLM</li>
 * </ul>
 */
package com.finhub.fundflow;