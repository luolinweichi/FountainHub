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
 * 获奖记录（对应数据库的 `awards` 表）—— T-032（M8）。
 *
 * ===========================================================================
 * 【它只存"这场比赛得了什么奖"，不存"谁得的"】
 *   获奖与学员的关系放在 `award_students`（一条奖可关联多人 = 团队奖），
 *   证书照片放在 `award_images`（可多张）。
 *
 *   ⭐ 这样拆的理由：**公开意愿是"每个人的"**，不是"这条奖的" ——
 *      同一条团队奖里，甲同意公开、乙不同意，是完全正常的。
 *      所以 `is_public` 必须放在关联表 `award_students` 上（一期数据库就定死了这个设计）。
 *
 * 【字段与数据库的对应】见 sql/01-schema.sql 第 229~243 行。
 *   `award_date` 是 **DATE**（不是 DATETIME）→ Java 用 LocalDate。
 *   `created_by` 是"录入人"（users.id），和全项目一致用**裸 Long**，不做 @ManyToOne 关系映射。
 * ===========================================================================
 */
@Entity
@Table(name = "awards")
@Getter
@Setter
@NoArgsConstructor
public class Award {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 比赛名称（必填） */
    @Column(name = "competition_name", nullable = false, length = 200)
    private String competitionName;

    /** 举办方（可空） */
    @Column(name = "organizer", length = 200)
    private String organizer;

    /** 获奖日期（可空）—— 数据库是 DATE */
    @Column(name = "award_date")
    private LocalDate awardDate;

    /** 级别：校级 / 市级 / 省级 / 国家级 / 国际级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "award_level", nullable = false, length = 20)
    private AwardLevel awardLevel;

    /** 奖项等级：一等 / 二等 / 三等 / 优秀 / 参与 */
    @Enumerated(EnumType.STRING)
    @Column(name = "prize", nullable = false, length = 20)
    private Prize prize;

    /** 指导老师姓名（可空）—— ⚠️ 存的是**姓名文本**，不是 users.id（表结构如此） */
    @Column(name = "advisor_name", length = 100)
    private String advisorName;

    /** 录入人（users.id）。裸 Long，同全项目其他实体 */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * 时间戳由**数据库**填（DEFAULT CURRENT_TIMESTAMP），我们只读不写。
     * `insertable/updatable = false` 的意思：生成 SQL 时**不带这两列**，
     * 交给 MySQL 的默认值和 ON UPDATE 去维护（比应用层传时间可靠，也不怕时区问题）。
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
