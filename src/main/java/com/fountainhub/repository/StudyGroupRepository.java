package com.fountainhub.repository;

import com.fountainhub.entity.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 学习小组仓库（T-015）。
 *
 * 【为什么只有这么点内容】
 *   Spring Data JPA 的 `JpaRepository` 已经免费提供了一整套方法：
 *     save / findAll / findById / deleteById / count ……
 *   绝大多数场合**一个方法都不用写**。只有"固定的查询"才需要加 ——
 *   加的方式是**写方法名**，Spring 按名字自动生成 SQL（叫"派生查询"）。
 *
 * 【为什么加 findAllByOrderBySortOrderAsc】
 *   组列表要按 `sort_order` 升序显示（数据库设计就是这个意图）。
 *   写在这里等于把"显示顺序"这件事**明确记下来**，T-016 直接用，不用每次自己想排序。
 *
 *   方法名拆开看（Spring 的命名规则）：
 *     findAllBy      → 查全部
 *     OrderBy        → 按……排序
 *     SortOrder      → `sortOrder` 这个属性
 *     Asc            → 升序
 *
 * 【T-016 可能会加的】按组名查重（新建组时要判断重名）—— 到时候再加，现在不加（YAGNI）。
 */
public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    /** 按显示顺序（sort_order）升序取全部小组 */
    List<StudyGroup> findAllByOrderBySortOrderAsc();

    /**
     * 组名是否已存在（T-016）—— 新建组时用来查重，给出友好提示，
     * 而不是让数据库的 UNIQUE 约束抛异常变成 500。
     */
    boolean existsByName(String name);

    /**
     * 这个人是不是**某个组的组长**（T-016）。
     *
     * ⚠️ **方法名必须是 `existsByLeaderId`，不能写 existsByLeaderUserId**
     *   因为实体里的属性叫 `leader`（一个 `@ManyToOne User` 关联），
     *   并没有 `leaderUserId` 这个属性 —— 写了会让 **Spring 启动直接失败**
     *   （BeanCreationException: studyGroupRepository）。**2026-10-02 实测踩过。**
     *   派生查询用的是 **Java 属性名**，顺着关联能点下去：`leader.id` → `LeaderId`。
     *
     * 【它在哪里用】`DbUserDetailsService` 登录时调用：
     *   是组长 → 额外授予 `ROLE_LEADER`。
     *   这就是"指定组长 = 加上组长角色"的实现方式 —— **不需要动 users.role**，见 Role.assignable() 的注释。
     */
    boolean existsByLeaderId(Long userId);

    /**
     * 取这个人**带的全部组**（T-017）。
     *
     * 【为什么返回 List 而不是 Optional】一个人理论上可以带多个组（虽然现在很少见），
     *   用 List 就不用为"万一有两个"再改一次。
     *
     * 【它在哪里用】`MyGroupController`：「我的小组」页面靠它找出"我带的组"。
     *   返回空列表 = 这个人不是任何组的组长。
     */
    List<StudyGroup> findByLeaderId(Long userId);
}
