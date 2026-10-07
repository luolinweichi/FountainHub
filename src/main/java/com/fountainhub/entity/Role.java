package com.fountainhub.entity;

/**
 * 用户角色。
 *
 * 【为什么用枚举，而不是直接写 String】
 *   如果写成字符串，把 "PRESIDENT" 打成 "PRESIDANT"，编译器不会报错，
 *   要等运行到权限判断时才发现 —— 而权限出错的代价很大。
 *   用枚举：打错字【编译就报错】。
 *
 * 【为什么数据库里存的是字符串】
 *   @Enumerated(EnumType.STRING) 会把枚举名原样存成 'PRESIDENT'。
 *   这样直接打开数据库就能看懂，不用查代码（见 docs/05 第 1 节设计原则 7）。
 *
 * 【为什么只有 5 种角色】
 *   需求里说的「社长（兼后勤）」【不是一个独立角色】，而是在 PRESIDENT /
 *   VICE_PRESIDENT 上打一个 logistics_duty 标签 —— 这与 sql/01-schema.sql 一致。
 */
public enum Role {

    /** 指导老师（信息技术处，全部权限） */
    TEACHER("指导老师"),

    /** 正社长（超级管理员） */
    PRESIDENT("正社长"),

    /** 副社长（不能删数据、不能管理账号） */
    VICE_PRESIDENT("副社长"),

    /** 组长（只能读写本组） */
    LEADER("组长"),

    /** 学员（只能看自己） */
    STUDENT("学员");

    /** 中文名，界面上显示用 */
    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 可以被【直接指派】的角色 —— 也就是"角色下拉框里能选的那些"（T-016）。
     *
     * ⚠️ **故意不包含 LEADER（组长）**
     *
     * 项目负责人 2026-10-02 定了「一个人可以同时担任几种角色」，采用的方案是：
     * **「组长」这个身份不从 `users.role` 里存，而是从 `study_groups.leader_user_id` 派生出来。**
     *
     *   · 指定某人当组长 → 把 `leader_user_id` 设成他 → 他登录时**自动多一个 ROLE_LEADER**
     *   · 把他换下来     → 清掉 `leader_user_id` → ROLE_LEADER **自动消失**
     *
     * 【为什么这么做，而不是单独存一份角色】
     *   因为「谁是组长」的**唯一真相**本来就在 study_groups 表里。
     *   如果 users.role 里也存一份，就变成两份数据 —— 两份就会不一致
     *   （比如角色被降成学员，但 leader_user_id 还挂着他）。
     *
     * 【好处】不用改数据库结构，而且能自然表达「学员 + 组长」「社长 + 组长」这种兼任。
     */
    public static Role[] assignable() {
        return new Role[]{ TEACHER, PRESIDENT, VICE_PRESIDENT, STUDENT };
    }
}
