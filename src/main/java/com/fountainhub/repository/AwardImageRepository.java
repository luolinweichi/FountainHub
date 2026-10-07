package com.fountainhub.repository;

import com.fountainhub.entity.AwardImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 获奖证书图片仓库（T-032）。
 *
 * 保存策略：**清空重建**（先把这条奖的图删掉，再按表单提交的顺序重新插入）。
 * 【为什么这里可以用清空重建，而成员关联不行】
 *   图片没有"意愿"这类**属于某个人**的状态，重建只是换个顺序；
 *   而 award_students 上的 is_public 一旦被删就没了 —— 所以那边必须差量同步。
 */
public interface AwardImageRepository extends JpaRepository<AwardImage, Long> {

    /** 某条获奖的图，按排序号升序 */
    List<AwardImage> findByAwardIdOrderBySortOrderAscIdAsc(Long awardId);

    /** 证书张数（列表页显示） */
    long countByAwardId(Long awardId);

    /** 清空重建前先把这条奖的图都取出来（再逐个 delete） */
    List<AwardImage> findByAwardId(Long awardId);
}
