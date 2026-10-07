package com.fountainhub.entity;

/**
 * 考勤结果（T-018）—— 对应 `attendance_records.status` 这一列。
 *
 * 【和"签没签到"的区别】
 *   "有没有这条记录" = 他有没有来（**没来就没有记录**，这是需求：没去就空着）；
 *   而这条记录一旦存在，它的 status 说明他是 到场 / 缺席 / 请假。
 *
 * ⚠️ 枚举名必须和数据库注释一字不差（PRESENT / ABSENT / LEAVE）。
 */
public enum AttendanceStatus {

    /** 到场 */
    PRESENT("到场"),

    /** 缺席 */
    ABSENT("缺席"),

    /** 请假 */
    LEAVE("请假");

    private final String label;

    AttendanceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
