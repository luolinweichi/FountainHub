package com.fountainhub.repository;

import com.fountainhub.entity.Award;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 获奖仓库（T-032）。
 *
 * ⚠️ 派生查询写的是 **Java 属性名**（awardDate / createdAt），不是数据库列名。
 *
 * 【为什么只有一个查询方法】
 *   获奖记录是**几十条**的量级，所以"按级别筛选""按年份筛选"全部在
 *   Controller 里**内存过滤**（和 AdminHomeController 统计打卡同一套思路：
 *   一次查完，避免为每种筛选组合写一条查询、也避免 N+1）。
 *   真到了几千条再换成分页 + 数据库筛选。
 */
public interface AwardRepository extends JpaRepository<Award, Long> {

    /**
     * 列表：按**获奖日期倒序**（最近的在前），日期为空的排在最后，再按 id 倒序保证稳定。
     *
     * 【为什么 null 排后面】MySQL 里 ORDER BY ... DESC 时 NULL 视为最小值，
     * 所以没有填日期的记录会自然落到最后 —— 正是我们想要的（"日期未知"不该顶在最上面）。
     */
    List<Award> findAllByOrderByAwardDateDescIdDesc();
}
