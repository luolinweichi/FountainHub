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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤活动（对应数据库的 attendance_events 表）—— T-018。
 *
 * 【它是干什么的】
 *   一张"今天机房开不开、算不算必须来"的公告：
 *     · WEEKLY_MEETING 例行活动（全员必到）
 *     · DAILY_OPEN     周一到周五的日常开放（自愿）
 *
 * 【"打卡"到底是什么（2026-10-02 项目负责人澄清）】
 *   打卡 = **"你到机房了没有"**，不是"今天做了几道题"。
 *   学员在一场活动里点一下自助签到 → 产生一条 AttendanceRecord。
 *   **没去就什么都不产生**（需求原话："没去就空着"）—— 我们不会替他自动补一条缺席记录。
 *
 * 【数据库上的重要约束】
 *   UNIQUE (event_date, type) —— **同一天、同一种活动只能有一场**。
 *   也就是说 2026-10-07 不能有两场"例行活动"。建活动前要先查重（见仓库的 findByEventDateAndType）。
 */
@Entity
@Table(name = "attendance_events")
@Getter
@Setter
@NoArgsConstructor   // JPA 规范要求实体必须有无参构造器
public class AttendanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 活动标题，如「例行活动」 */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** 活动日期 */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** 活动类型：必到的例行活动 / 自愿的日常开放 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AttendanceType type;

    /** 说明（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 创建人（users.id）。**用裸 Long，不用 @ManyToOne** ——
     * 和 User.groupId / StudentProfile 的风格一致，避免模板里踩懒加载的坑。
     * 真要显示"谁创建的"时（T-020），一次性把人名查出来做成 Map 即可。
     */
    @Column(name = "created_by")
    private Long createdBy;

    /** 创建时间。数据库自己填，我们只读不写 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
