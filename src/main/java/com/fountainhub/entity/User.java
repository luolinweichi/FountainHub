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

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应数据库里的 users 表。
 *
 * 【这是什么】
 *   JPA 的「实体类」：一个 Java 对象 = 数据库里的一行记录。
 *   加上 @Entity 之后，Hibernate 就知道"这个类对应一张表"。
 *
 * 【为什么整个系统只有一张用户表】
 *   登录时系统还不知道来的人是老师还是学生。如果分成 teachers / students /
 *   presidents 三张表，每次登录都得挨个查三遍。
 *   【一张表 + role 字段 = 一次查询搞定】。
 *
 * 【为什么用 Lombok 而不是手写 getter/setter】
 *   13 个字段的 getter/setter 手写约 130 行，全是机械重复。
 *   Lombok 在编译期自动生成，源码保持干净。
 *   注意：本项目规定只用 @Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor，
 *   禁止 @Data（它会生成 equals/hashCode，对 JPA 实体是坑）。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor   // JPA 规范要求实体必须有无参构造器：Hibernate 用反射创建对象时要用它
public class User {

    /** 主键。IDENTITY = 交给数据库的 AUTO_INCREMENT 生成 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，唯一，不能为空 */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /** 学号，唯一，可为空（管理员账号没有学号） */
    @Column(name = "student_no", length = 30)
    private String studentNo;

    /**
     * 手机号，唯一，可为空。
     * ⚠️ 硬性红线：任何页面都不得展示手机号（见 docs/spec.md 第 3.3 节）
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /** BCrypt 密码哈希。字段名就叫 hash —— 提醒所有人"这里绝不存明文" */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 真实姓名 */
    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    /** 角色。@Enumerated(STRING) = 数据库里存 "PRESIDENT" 这样的字符串，不存数字 */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    /**
     * 是否兼管后勤。
     * 注意：它【不是角色】，只是角色上的一个标签（见 Role 的注释）。
     */
    @Column(name = "logistics_duty", nullable = false)
    private Boolean logisticsDuty = false;

    /**
     * 所属小组的 ID。
     * 【为什么这里先存 ID，而不是直接存 StudyGroup 对象】
     *   存对象要处理"多对一"关联和延迟加载，是 T-015 的事。
     *   本任务只做最直白的映射，先把地基打稳。
     */
    @Column(name = "group_id")
    private Long groupId;

    /** 账号状态。默认 ACTIVE（在读） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * 首次登录是否【必须先改密码】（2026-10-07 新增）。
     *
     * 数据库列：must_change_password TINYINT(1) NOT NULL DEFAULT 1
     *           （脚本见 sql/06-add-must-change-password.sql）
     *
     * · 建号时默认 true → 用管理员给的初始密码登录后**必须**先改成自己的密码
     * · 在 /me/password 改成功后由程序置为 false（见 PasswordController）
     * · 拦人逻辑在 security/MustChangePasswordInterceptor
     *
     * ⚠️ 和 status 一样，这是"账号级"的开关，**不是角色**。
     * ⚠️ 字段必须和数据库列同名同类型，否则 ddl-auto: validate 会让应用启动失败。
     */
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = true;

    /** 最后登录时间，可为空（从没登录过就是 null） */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间。
     * insertable=false, updatable=false 的意思是：
     *   「程序不许写这一列」—— 数据库的 DEFAULT CURRENT_TIMESTAMP 会自己填。
     * 好处：不管从哪个入口插入，创建时间都由数据库统一说了算，不会被程序改乱。
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间。同上。
     * 数据库那边写的是 ON UPDATE CURRENT_TIMESTAMP，每次 UPDATE 自动刷新。
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
