package com.fountainhub.repository;

import com.fountainhub.entity.User;
import com.fountainhub.entity.UserStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * 用户查询条件（"可拼装的筛选项"）。
 *
 * 【为什么需要这个类：组合爆炸】
 *   学员列表有三个筛选项（关键词 / 状态 / 组）。用户可能只用其中一个，
 *   也可能三个一起用 —— 一共 2³ = 8 种组合。
 *   如果按老办法"一个组合写一个查询方法"，就要写 8 个方法；
 *   将来再加一个筛选项就变成 16 个。这叫组合爆炸。
 *
 *   Specification 把每个筛选项变成【一小段独立的条件】，
 *   用的时候现拼 —— 一个方法就能应对所有组合。
 *
 * 【它和 Spring Data 的"派生查询"有什么不同】
 *   派生查询（比如 T-002 的 findByUsername）适合【固定】的查询：方法名 = SQL，写死。
 *   Specification 适合【动态】的查询：条件在运行时才决定。
 *   两种都要会，用在对的地方。
 *
 * 【返回 null 是什么意思】
 *   每个方法在"这个条件不存在"时返回 null。
 *   Specification 的约定是：null = 这个条件【不参与】过滤。
 *   所以"只填了关键词、没选状态"时，状态那一项自然就被忽略了。
 */
public final class UserSpecifications {

    /**
     * 私有构造器：这是一个纯工具类，只提供静态方法，不应该被 new。
     */
    private UserSpecifications() {
    }

    /**
     * 关键词：同时匹配【姓名】或【学号】。
     *
     * ⚠️ 只搜 realName 和 studentNo，【绝不搜 phone】。
     *    如果这里加上手机号，就等于对外提供了一个"手机号查询接口"，
     *    直接违反 docs/spec.md 第 3.3 节的硬性红线。
     *
     * @param keyword 用户输入的关键词；为空则这个条件不参与
     */
    public static Specification<User> keyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;                       // 不参与过滤
            }
            // 转小写 + 前后加 %：实现"不区分大小写的模糊匹配"
            // 例如输入 "test" 能匹配 "test001"、"Test002"
            String like = "%" + keyword.trim().toLowerCase() + "%";
            // cb.or(...) = 姓名匹配【或】学号匹配，命中一个就算
            return cb.or(
                    cb.like(cb.lower(root.<String>get("realName")), like),
                    cb.like(cb.lower(root.<String>get("studentNo")), like));
        };
    }

    /**
     * 状态：在读 / 退社 / 毕业 / 已禁用。
     *
     * @param status 选中的状态；null 表示"全部"（这个条件不参与）
     */
    public static Specification<User> status(UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /**
     * 组：只看某个组的人（T-016 补上 —— 类注释里说的"三个筛选项"到齐了）。
     *
     * @param groupId 选中的组 id；null 表示"全部"（这个条件不参与）
     *
     * 【⚠️ 为什么不支持"找未分组的人"】当前界面只提供"全部 / 某个组"两种选择。
     *   真要支持"未分组"，条件得写成 `root.get("groupId").isNull()`，是另一个分支。
     *   现在不需要，就不写（YAGNI）。
     */
    public static Specification<User> group(Long groupId) {
        return (root, query, cb) -> groupId == null ? null : cb.equal(root.get("groupId"), groupId);
    }
}
