package com.fountainhub.repository;

import com.fountainhub.entity.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 忘记密码申请仓库（T-012B）。
 *
 * 【为什么方法名这么长】Spring Data 的"派生查询"靠方法名生成 SQL：
 *   findByStatusOrderByCreatedAtDesc → WHERE status = ? ORDER BY created_at DESC
 *   不用自己写一行 SQL，也不容易写错。
 */
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    /** 后台审核区用：待审核的申请，最新的排前面 */
    List<PasswordResetRequest> findByStatusOrderByCreatedAtDesc(String status);

    /** 提交新申请前，找这个人已有的待审核申请（要把它们作废，只留最新一条） */
    List<PasswordResetRequest> findByUserIdAndStatus(Long userId, String status);

    /** 查某人最新的申请（用来决定 /forgot 页面显示"空闲"还是"已提交"） */
    Optional<PasswordResetRequest> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
