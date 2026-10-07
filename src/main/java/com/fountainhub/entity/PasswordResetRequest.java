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
 * 忘记密码【申请】（映射 users 之外的第 19 张表 password_reset_requests）。
 *
 * ===========================================================================
 * 【为什么单独建一张表，而不是直接在 users 上挂个字段】
 *   一个人可能反复提交（第一次写错密码、第二次才对），历史上要留痕。
 *   而且"待审核的申请"本身是一条独立的事务，和用户是 1:N 的关系。
 *
 * 🔒 【本功能最关键的一条规则】
 *   学员提交时，新密码**立刻 BCrypt 哈希**存进这张表的 new_password_hash，
 *   但 **在社长审核通过之前，绝不能写回 users.password_hash**。
 *   否则就等于"学员自己改密码立即生效"，**绕过了审核**。
 * ===========================================================================
 */
@Entity
@Table(name = "password_reset_requests")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetRequest {

    /** 申请状态：待审核 / 已通过 / 已拒绝 */
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请人（users.id）。外键 ON DELETE CASCADE：人没了申请也没意义 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 学员想设置的新密码（BCrypt 哈希，绝不存明文）。
     * ⚠️ 字段长度 100 是照着表结构写的（BCrypt 输出 60 位，留余量）。
     */
    @Column(name = "new_password_hash", nullable = false, length = 100)
    private String newPasswordHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status = PENDING;

    /**
     * 审核人（社长）。
     * 【为什么是弱引用】按 docs/05 的设计原则 6：故意不建外键，
     * 避免以后删用户时连带影响历史审核记录。
     */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 255)
    private String reviewNote;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 自己填，程序不许写 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
