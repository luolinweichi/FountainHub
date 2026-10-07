package com.fountainhub.entity;

/**
 * 打卡方式（T-018）—— 对应 `attendance_records.checkin_type` 这一列。
 *
 * 【为什么需要它】需求明确要求：**社长代打卡时必须记录"是谁代打的"**。
 *   所以记录里要能区分"自己打的"和"别人代打的"：
 *     SELF  → `operator_id` 为空（就是本人）
 *     ADMIN → `operator_id` 填代打卡的那个人
 *
 * ⚠️ 枚举名必须和数据库注释一字不差（SELF / ADMIN）。
 */
public enum CheckinType {

    /** 自助打卡（本人操作） */
    SELF("自助"),

    /** 管理员代打卡（`operator_id` 记录是谁代打的） */
    ADMIN("管理员代打");

    private final String label;

    CheckinType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
