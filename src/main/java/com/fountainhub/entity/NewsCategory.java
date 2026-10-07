package com.fountainhub.entity;

/**
 * 新闻分类（T-023）—— 对应 news.category。
 * ⚠️ 枚举名必须和数据库注释一字不差（ACTIVITY / NOTICE / CONTEST / TECH）。
 */
public enum NewsCategory {

    ACTIVITY("活动"),
    NOTICE("通知"),
    CONTEST("比赛"),
    TECH("技术分享");

    private final String label;

    NewsCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
