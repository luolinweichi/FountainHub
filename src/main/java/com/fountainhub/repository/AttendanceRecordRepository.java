package com.fountainhub.repository;

import com.fountainhub.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 考勤记录仓库（T-018）。
 *
 * 同样只加"马上要用"的方法。⚠️ 方法名用 Java 属性名（eventId / userId / checkinAt），
 * 不是数据库列名 —— 写错会让应用启动直接失败。
 */
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /**
     * 找"某人在某场活动里的记录"。
     *
     * 【它在哪里用 ①】T-019 签到页：判断"我这场签过没" —— 签过就显示已签到，不重复插入。
     * 【它在哪里用 ②】这就是表上 UNIQUE (event_id, user_id) 那条约束的查询方式。
     */
    Optional<AttendanceRecord> findByEventIdAndUserId(Long eventId, Long userId);

    /**
     * 取某人全部考勤记录，按打卡时间倒序（最近的在前）。
     *
     * 【它在哪里用】T-019「我的考勤记录」列表。
     */
    List<AttendanceRecord> findByUserIdOrderByCheckinAtDesc(Long userId);

    /**
     * 某一场活动的全部签到记录（T-020 单场明细页用）。
     */
    List<AttendanceRecord> findByEventId(Long eventId);
}
