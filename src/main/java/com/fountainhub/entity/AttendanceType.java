package com.fountainhub.entity;

/**
 * 考勤活动类型（T-018）—— 对应 `attendance_events.type` 这一列。
 *
 * 【为什么用枚举】写错一个字母【编译就报错】，而不是等运行时才发现 —— 和 Role / UserStatus 一个道理。
 *
 * ⚠️ 枚举名必须和数据库注释里的一字不差（WEEKLY_MEETING / DAILY_OPEN），
 *    改一个字母，数据库里已有的数据就读不出来了。
 */
public enum AttendanceType {

    /** 例行活动：全员**必到**（周几由你自己定）*/
    WEEKLY_MEETING("例行活动（必到）"),

    /** 日常开放：**自愿** */
    DAILY_OPEN("日常开放（自愿）");

    private final String label;

    AttendanceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
