package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 考勤记录（对应数据库的 attendance_records 表）—— T-018。
 *
 * 【一条记录 = 某人在某场活动里的一次签到】
 *
 * 【两条需求直接体现在这张表上】
 *   1. **没去就空着** —— 没签到的人**不会有**任何记录。
 *      我们不会自动替他生成一条"缺席"，因为需求就是"没去就不打卡"。
 *   2. **社长代打卡要记录是谁代打的** ——
 *        checkinType = SELF  → operatorId 为空（本人打的）
 *        checkinType = ADMIN → operatorId = 代他打卡的那个人
 *      页面上就能显示"由 XX 代打"。
 *
 * 【数据库上的重要约束】
 *   UNIQUE (event_id, user_id) —— **同一场活动，同一个人只能有一条记录**。
 *   所以签到前要先查"我签过没"，签过了就不重复插入（见仓库的 findByEventIdAndUserId）。
 */
@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 属于哪场活动（attendance_events.id）。裸 Long，同 AttendanceEvent.createdBy 的理由 */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** 谁（users.id）。裸 Long */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 到场 / 缺席 / 请假 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    /** 自助打卡 还是 管理员代打卡 */
    @Enumerated(EnumType.STRING)
    @Column(name = "checkin_type", nullable = false, length = 20)
    private CheckinType checkinType = CheckinType.SELF;

    /** 代打卡的操作人（users.id）。自助打卡时为空 */
    @Column(name = "operator_id")
    private Long operatorId;

    /**
     * 打卡时间。**数据库自己填（DEFAULT CURRENT_TIMESTAMP），我们只读不写**。
     *
     * 【为什么不让我们写】需求要的是"记录**何时**打的" ——
     * 由数据库在插入那一刻自动记下来，比应用层传时间更可靠
     * （不用担心服务器时区、也不用担心有人伪造时间）。
     */
    @Column(name = "checkin_at", insertable = false, updatable = false)
    private LocalDateTime checkinAt;
}
