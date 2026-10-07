package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 学习小组（对应数据库的 `study_groups` 表）。
 *
 * 【它是干什么的】
 *   本系统按学习方向分组（Python 组 / C 语言组 / 将来的竞赛组），
 *   每个组有一个「组长」，组员通过 `users.group_id` 指向这里。
 *
 * 【和另外两个实体的三个不同（都是刻意的，不是笔误）】
 *
 *   1. **主键是数据库自增的**（`@GeneratedValue(IDENTITY)`）
 *      —— 和 User 一样。
 *      对比：`StudentProfile` 的主键【就是】外键（user_id），跟着人走，不能自增。
 *
 *   2. **leader 用 `@ManyToOne` 关联，而不是裸 Long**
 *      —— 因为界面要显示「组长：张三」，用裸 ID 就得再查一次 users 表。
 *
 *   3. **关联刻意用 EAGER（默认值），【不要】改成 LAZY** ⚠️
 *      —— R-012 已经把 Open-Session-In-View 关掉了。一旦这里改成 LAZY，
 *         在 Thymeleaf 模板里访问 `group.leader.realName` 就会抛
 *         `LazyInitializationException`（因为渲染页面时 Hibernate 会话已经关了）。
 *         分组表只有个位数行，EAGER 完全没负担。
 */
@Entity
@Table(name = "study_groups")
@Getter
@Setter
@NoArgsConstructor   // JPA 规范要求实体必须有无参构造器：Hibernate 用反射创建对象时要用它
public class StudyGroup {

    /** 主键，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 组名，如「Python 组」。数据库上有 UNIQUE 约束 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 说明，如「以 Python 为主要学习语言的小组」 */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 组长。**可以为 null** —— 新建的组还没指定组长时就是空的。
     *
     * 数据库那一列是 `leader_user_id`，外键指向 `users(id)`，
     * 且是 `ON DELETE SET NULL`：删掉组长账号时，这个组不会跟着消失，只是变成"没有组长"。
     *
     * ⚠️ 关于 EAGER / LAZY 的选择，见类注释第 3 条 —— **不要改成 LAZY**。
     */
    @ManyToOne
    @JoinColumn(name = "leader_user_id")
    private User leader;

    /** 显示排序（小的排前面）。数据库默认 0 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 创建时间。数据库自己填（DEFAULT CURRENT_TIMESTAMP），所以我们只读不写 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间。数据库自己维护（ON UPDATE CURRENT_TIMESTAMP），所以我们只读不写 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
