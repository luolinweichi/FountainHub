package com.fountainhub.entity;

/**
 * 新闻状态（T-023）—— 对应 news.status。
 *
 * 【为什么这是个关键字段】需求明确要求「**草稿状态的新闻游客访问不到**」，
 *   也就是说**草稿必须只有后台能看到**。T-026 的公开页会按这个字段过滤。
 *
 * ⚠️ 枚举名必须和数据库注释一字不差（DRAFT / PUBLISHED）。
 */
public enum NewsStatus {

    /** 草稿：只有社长层在后台能看见 */
    DRAFT("草稿"),

    /** 已发布：对外可见（除非 is_internal = true） */
    PUBLISHED("已发布");

    private final String label;

    NewsStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
