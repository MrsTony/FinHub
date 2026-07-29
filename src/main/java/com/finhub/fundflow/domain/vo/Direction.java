package com.finhub.fundflow.domain.vo;

/**
 * 资金流向：IN（资金流入）或 OUT（资金流出）。
 *
 * <p>注意：IN/OUT 是资金流向，非收支口径。退款是 IN（资金流入）但不是收入，
 * 而是冲减原支出；真收入由 Category=INCOME 且方向 IN 判定。
 * 收支统计须按 Category 口径，不可简单按方向汇总。</p>
 */
public enum Direction {
    IN, OUT
}