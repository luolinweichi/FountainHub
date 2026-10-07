package com.fountainhub.repository;

import com.fountainhub.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 学员档案仓库。
 *
 * 【为什么现在一个自定义方法都没有】
 *   StudentProfile 的主键【就是】 user_id，所以：
 *       "按用户 ID 查档案"  →  findById(userId)     （JpaRepository 自带）
 *       "新增 / 保存档案"   →  save(profile)         （自带）
 *       "删除档案"          →  deleteById(userId)    （自带）
 *   目前能想到的用法全都有现成的，硬加一个 findByUserId 反而是重复造轮子。
 *
 * 【什么时候再加方法】
 *   T-011「新增学员」、T-013「我的资料」真用到别的查询时再加
 *   （例如"查所有还没填档案的学员"）。按 AGENTS.md 第 4 节：不做顺手的事。
 */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
}
