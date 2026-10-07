package com.fountainhub.repository;

import com.fountainhub.entity.AwardStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 获奖-学员关联仓库（T-032）。
 *
 * 【这个仓库承担两件重要的事】
 *   1. 后台编辑时的**差量同步**：先查出现有的关联行，再和表单提交的名单比对
 *      → 保留（不动 is_public）、新增（is_public 默认 false）、删除（未勾选的）。
 *      ⚠️ 绝不能用"清空重建"—— 那会把学员已经打开的公开意愿抹掉（红线相关）。
 *   2. 学员端：「我有哪些获奖」= findByUserId...（只能查自己的）
 */
public interface AwardStudentRepository extends JpaRepository<AwardStudent, Long> {

    /** 某条获奖下的所有关联（后台编辑页、列表页统计用） */
    List<AwardStudent> findByAwardId(Long awardId);

    /** 我自己的获奖关联（学员端 `/me/awards`）—— 只按当前登录用户查，天然隔离 */
    List<AwardStudent> findByUserIdOrderByIdDesc(Long userId);

    /** 某条获奖关联了几个人（列表页显示"参与 N 人"） */
    long countByAwardId(Long awardId);

    /** 落库前去重用：这条奖里是不是已经有这个人了 */
    boolean existsByAwardIdAndUserId(Long awardId, Long userId);
}
