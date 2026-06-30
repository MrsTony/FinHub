/**
 * 本地知识库上下文（knowledge）— 支撑域
 *
 * <h3>职责边界</h3>
 * <p>管理 Document 聚合根的生命周期（上传→分块→Embedding→检索），
 * 为查询提供财务制度/历史凭证的语义上下文，不参与资金流水写入。</p>
 *
 * <h3>边界规则</h3>
 * <ul>
 *   <li>禁止直接依赖资金流水上下文</li>
 *   <li>不订阅资金流水的领域事件</li>
 *   <li>仅响应用户上传动作</li>
 * </ul>
 */
package com.finhub.knowledge;