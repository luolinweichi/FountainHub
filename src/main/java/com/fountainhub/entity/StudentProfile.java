package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学员档案实体 —— 对应数据库里的 student_profiles 表。
 *
 * 【为什么账号和档案要分成两张表】
 *   users 管【身份与权限】：用户名、密码、角色、状态 —— 所有角色（老师、社长、学员）都有。
 *   student_profiles 管【学员才有的信息】：年级、班级、QQ、入社日期、擅长语言、阶段。
 *   老师没有"年级"和"入社日期"，硬塞进 users 表就会留一堆永远为空的列。
 *   这也正是 docs/05 第 1 节设计原则 2「档案与账号分离」。
 *
 * 【一对一怎么体现】
 *   数据库里 student_profiles 的主键【就是】user_id，同时它又是外键指向 users.id。
 *   一个人只能有一条档案，一条档案只能属于一个人 —— 这就是一对一。
 *
 * 【为什么这里不用 @OneToOne，而是直接放一个 Long userId】
 *   1) 和 T-001 的 User.groupId 保持同一种风格：先把最直白的映射做稳，
 *      不急着引入关联映射（关联会带来延迟加载、N+1 查询这些新手坑）。
 *   2) T-010 的学员列表要的字段（姓名、学号、小组、状态）全都在 users 表里，
 *      暂时不需要跨表 join，也就暂时不需要"关系"。
 *   将来真的需要 profile.getUser() 时再升级，改动很局部。
 *
 * 【字段全部可空】
 *   数据库里除两个时间戳外，这些列都允许 NULL ——
 *   因为新学员入社时未必填得全。所以这里也都是包装类型/引用类型，不要用基本类型。
 */
@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor   // JPA 规范要求实体必须有无参构造器（Hibernate 用反射创建对象时要用）
public class StudentProfile {

    /**
     * 主键 —— 同时也是外键，指向 users.id。
     *
     * ⚠️ 这里【没有】@GeneratedValue：因为主键不是数据库自己生成的，
     *    而是"先有 users 里那个人，再把那个人的 id 拿过来当主键"。
     *    换句话说，档案是"跟着人走"的。
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 年级，例如「2025级」 */
    @Column(name = "grade", length = 20)
    private String grade;

    /**
     * 专业 / 班级。
     * ⚠️ Java 属性叫 majorClass（驼峰），数据库列叫 major_class（下划线），
     *    靠 @Column(name = ...) 把两者牵上线 —— 名字对不上，Hibernate 启动时就会报错。
     */
    @Column(name = "major_class", length = 50)
    private String majorClass;

    /**
     * 🔒 QQ / 微信 / 邮箱 —— 都是联系方式，属于个人隐私。
     *    处理原则与手机号【完全相同】：可以存、可以给本人和社长看，
     *    但【绝不能出现在任何公开页面】上（见 docs/spec.md 第 3.3 节硬性红线）。
     */
    @Column(name = "qq", length = 20)
    private String qq;

    /** 微信（同上，隐私，不得外显） */
    @Column(name = "wechat", length = 50)
    private String wechat;

    /** 邮箱（同上，隐私，不得外显） */
    @Column(name = "email", length = 100)
    private String email;

    /**
     * 入社日期。
     *
     * ⚠️⚠️ 数据库里这一列的类型是 DATE（只有年月日，没有时分秒），
     *      所以 Java 这边必须用 LocalDate，【不能】用 LocalDateTime。
     *      用错了语义就不对（"入社时间 2026-10-02 00:00:00" 这种没意义的精度）。
     *      这是本任务最容易搞错的一处。
     */
    @Column(name = "join_date")
    private LocalDate joinDate;

    /**
     * 擅长的编程语言，例如「Python,C++」。
     * 数据库设计就是用一个逗号分隔的字符串存的（不是多对多表），这里照原样映射。
     */
    @Column(name = "languages", length = 100)
    private String languages;

    /**
     * 所处阶段：入门 / 进阶 / 竞赛。
     * @Enumerated(EnumType.STRING) → 数据库里存 "BEGINNER" 这样的字符串，不存序号。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 20)
    private Level level;

    /**
     * 创建时间 / 更新时间。
     * insertable=false, updatable=false 的意思是「程序不许写这两列」——
     * 数据库的 DEFAULT CURRENT_TIMESTAMP 与 ON UPDATE CURRENT_TIMESTAMP 会自己填。
     * 好处：不管从哪个入口插入或更新，时间都由数据库统一说了算，不会被程序改乱。
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
