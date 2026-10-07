package com.fountainhub.repository;

import com.fountainhub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库 —— 负责「按条件把 users 表里的人找出来」。
 *
 * 【这是什么】
 *   JPA 的「Repository（仓库）」：你只声明「要什么」，不写「怎么查」。
 *   类比：users 表是一个档案柜，User 是抽屉里的一张卡片，
 *         这个接口就是档案管理员 —— 你说「按学号找」，他把人给你翻出来。
 *
 * 【为什么只写接口、不写实现】
 *   Spring Data JPA 在应用启动时会扫描所有继承了 JpaRepository 的接口，
 *   用动态代理自动生成实现类（所以这段代码里一行 SQL 都没有）。
 *
 *   你写的方法名不是随便起的 —— 它会被翻译成 SQL，这叫「派生查询」(derived query)：
 *
 *       findByUsername("zhangsan")  →  SELECT * FROM users WHERE username = 'zhangsan'
 *
 * 【⚠️ 最容易踩的坑：方法名里用的是 Java 属性名，不是数据库列名】
 *   ✅ findByStudentNo   （实体属性叫 studentNo；数据库列叫 student_no）
 *   ❌ findByStudent_no  （Spring Data 找不到名叫 student_no 的属性 → 应用启动直接失败）
 *
 * 【为什么返回 Optional<User>，而不是 User 或 List<User>】
 *   1) username / student_no / phone 三列在数据库里都建了 UNIQUE 索引，
 *      所以最多只能查出一行 —— 不会出现 List，用不着 List<User>。
 *   2) 用 Optional 是为了「强迫调用方处理查不到的情况」：
 *      登录时查不到 = 应该提示「用户名或密码错误」，
 *      而不是让程序抛 NullPointerException（那样页面会变成 500 错误）。
 *
 * 【什么时候会用到这里的方法】
 *   T-006「三种登录方式」：用户输入的那串字符，系统要分别按
 *   用户名 / 学号 / 手机号去查 —— 调用的就是下面这三个方法。
 *   所以本任务被称为「M1 账号与登录的地基」。
 */
public interface UserRepository extends JpaRepository<User, Long>,
                                   JpaSpecificationExecutor<User> {
    // ↑ T-010 新增的第二个父接口。
    //   JpaRepository        提供固定的增删改查（save / findById / count ...）
    //   JpaSpecificationExecutor 提供"拼装查询条件"的能力（findAll(Specification, Pageable)）
    //   后者是给"三个筛选项随意组合"用的，见 UserSpecifications.java 的注释。

    /**
     * 按登录用户名查一个人。
     *
     * @param username 登录用户名（对应 users.username，唯一）
     * @return 查到的人；查不到就是 Optional.empty()
     */
    Optional<User> findByUsername(String username);

    /**
     * 按学号查一个人。
     *
     * ⚠️ student_no 允许为 NULL（管理员账号没有学号）。
     *    如果调用方传进来 null，生成的 SQL 是 "WHERE student_no = null"，
     *    而 SQL 里 null = null 不成立 —— 结果是**静默地查不到**（不报错，这更危险）。
     *    → 调用方必须先判空，不要拿 null 来查。
     *
     * @param studentNo 学号（对应 users.student_no，唯一）
     * @return 查到的人；查不到就是 Optional.empty()
     */
    Optional<User> findByStudentNo(String studentNo);

    /**
     * 按手机号查一个人。
     *
     * ⚠️ 硬性红线：手机号**只用于登录与联系**，任何页面都不许展示
     *    （见 docs/spec.md 第 3.3 节）。这里只是「按手机号找人」，
     *    找到之后也不要把 phone 渲染到页面上。
     *
     * ⚠️ 同 findByStudentNo：phone 也允许为 NULL，传 null 会静默查不到。
     *
     * @param phone 手机号（对应 users.phone，唯一）
     * @return 查到的人；查不到就是 Optional.empty()
     */
    Optional<User> findByPhone(String phone);

    /**
     * 统计某个组有多少人（T-016）—— 分组管理页显示"组员人数"用。
     *
     * 方法名拆开看：count(计数) By(按) GroupId(groupId 这个属性)。
     * 注意用的是【Java 属性名 groupId】，不是数据库列名 group_id。
     */
    long countByGroupId(Long groupId);

    /**
     * 把所有用户按真实姓名排序列出来（T-016）—— 分组管理页的"指定组长"下拉框用。
     *
     * ⚠️ 下拉框里**只显示姓名，不显示手机号**（红线）。
     */
    List<User> findAllByOrderByRealNameAsc();

    /**
     * 取某个组的全部成员，按姓名排序（T-017）——「我的小组」页面列组员姓名用。
     *
     * 🔒 **调用方请注意**：这个页面上**只允许显示姓名**，
     *    不许把 `phone`（手机号）渲染出去 —— 见 docs/spec.md 第 3.3 节。
     *    这也是为什么 `me/group.html` 里根本没有写手机号那一列。
     */
    List<User> findAllByGroupIdOrderByRealNameAsc(Long groupId);
}
