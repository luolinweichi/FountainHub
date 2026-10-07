package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 获奖-学员关联（对应数据库的 `award_students` 表）—— T-032（M8）。
 *
 * ===========================================================================
 * ⭐⭐ 这张表是全项目**最需要小心的一张**，因为 `is_public` 是**红线字段**：
 *
 *   🔒 红线原话：「获奖对外展示必须经**该学员本人同意**」
 *      → 所以：
 *        1. 新建获奖时，**一律 `isPublic = false`（不同意）** —— 数据库默认值也是 0；
 *        2. **管理员不能替学员勾选**（后台表单里没有这个勾选框，只有只读状态）；
 *        3. 只有**学员自己**在「我的获奖」(`/me/awards`) 里才能打开/撤回；
 *        4. 服务端必须校验"这一行是不是你自己的"，否则能改别人的意愿。
 *
 * 【唯一约束】UNIQUE (award_id, user_id)
 *   → 同一条奖里同一个人只能出现一次。落库前必须**去重**，
 *     否则重复提交会抛异常（页面变 500，而不是人话提示）。
 *
 * 【外键】award_id / user_id 都是 ON DELETE CASCADE：
 *   删掉获奖 → 关联行自动消失；删掉用户 → 他的获奖关联也消失。
 *   ⚠️ 所以**编辑时移除某学员 = 删掉这一行 = 他之前打开的公开意愿一并丢失**
 *      （重新加回来会是"不同意"）—— 页面上必须提示这一点。
 * ===========================================================================
 */
@Entity
@Table(name = "award_students")
@Getter
@Setter
@NoArgsConstructor
public class AwardStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 属于哪条获奖（awards.id）。裸 Long */
    @Column(name = "award_id", nullable = false)
    private Long awardId;

    /** 获奖学员（users.id）。裸 Long */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 🔒 该学员**本人**是否同意把自己的这条获奖对外公开。
     * 默认 false（不同意）—— 只有学员本人在 `/me/awards` 里能改。
     */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    /** 由数据库填，只读 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
