package com.fountainhub.repository;

import com.fountainhub.entity.AttendanceEvent;
import com.fountainhub.entity.AttendanceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 考勤活动仓库（T-018）。
 *
 * 【为什么只有两个方法】
 *   只加"马上要用"的。JpaRepository 已经免费提供了 save / findAll / findById 等一整套，
 *   绝大多数场合一个方法都不用写。
 *
 * 【方法名 = SQL（派生查询）】
 *   ⚠️ 用的是 **Java 属性名**，不是数据库列名：
 *      findByEventDateAndType  ✅（属性叫 eventDate / type；列叫 event_date / type）
 *      findByEvent_date...     ❌（Spring Data 找不到这个属性 → **应用启动直接失败**）
 *    这个坑在 T-016 踩过一次（existsByLeaderUserId），务必小心。
 */
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    /**
     * 按"日期 + 类型"找一场活动。
     *
     * 【它在哪里用】建活动前**查重** —— 表上有 UNIQUE (event_date, type)，
     *   直接撞上去会抛异常变成 500；提前查一遍能给一句人话提示（T-020 用）。
     */
    Optional<AttendanceEvent> findByEventDateAndType(LocalDate eventDate, AttendanceType type);

    /**
     * 取某段时间内的所有活动，按日期升序。
     *
     * 【它在哪里用】T-019 学员签到页要显示"今天 / 本周有哪些日常开放活动"。
     */
    List<AttendanceEvent> findByEventDateBetweenOrderByEventDateAsc(LocalDate from, LocalDate to);

    /**
     * 取某种类型的全部活动，按日期升序（T-019）。
     *
     * 【它在哪里用】算「**连续来了几周例行活动**」——
     *   把全部 WEEKLY_MEETING 按日期排好，从最近的往回数，签了就 +1、没签就停。
     */
    List<AttendanceEvent> findByTypeOrderByEventDateAsc(AttendanceType type);

    /**
     * 全部活动，**日期倒序**（最近的在前）。
     *
     * 【它在哪里用】T-020 后台考勤管理页的活动列表 —— 社长最关心最近几场。
     */
    List<AttendanceEvent> findAllByOrderByEventDateDesc();
}
