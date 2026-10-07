package com.fountainhub.entity;

/**
 * 获奖「奖项等级」（T-032）—— 对应 `awards.prize` 这一列。
 *
 * ⚠️ 枚举名必须和数据库注释一字不差
 *   （FIRST / SECOND / THIRD / EXCELLENT / PARTICIPATION），见 sql/01-schema.sql 第 235 行。
 */
public enum Prize {

    FIRST("一等奖"),
    SECOND("二等奖"),
    THIRD("三等奖"),
    EXCELLENT("优秀奖"),
    PARTICIPATION("参与奖");

    private final String label;

    Prize(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
