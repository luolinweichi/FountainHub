package com.fountainhub.entity;

/**
 * 账号状态。
 *
 * PENDING  待审核 —— 学员自助注册后的初始状态（不能登录，等管理员审核）
 * ACTIVE   在读 —— 能登录
 * LEFT     退社 —— 保留数据，【可以】登录，但只能看公开页面和自己的个人主页；后台一律 403
 * GRADUATED 毕业 —— 保留数据，【可以】登录（项目负责人 2026-10-02 决定：毕业生能回来看记录）
 * DISABLED 已禁用 —— 管理员手动停用（比如违规）
 *
 * 【为什么要区分这么细，而不是简单"能登录/不能登录"】
 *   社团每年都有人毕业、有人退社。如果直接删账号，
 *   他的打卡记录、获奖记录、考勤记录就全没了（外键会连带删除）。
 *   所以用状态标记，【数据永远留着】。
 */
public enum UserStatus {

    /**
     * 待审核 —— 学员自助注册后的初始状态。
     * 【为什么它能自动"登不进去"】
     *   登录白名单在 DbUserDetailsService 里写死为 {ACTIVE, GRADUATED, LEFT}，
     *   所以 PENDING 天然不在其中，【一行权限代码都不用改】。
     */
    PENDING("待审核"),

    ACTIVE("在读"),

    /**
     * 退社 —— 【2026-10-07 起可以登录了】。
     *
     * 【能看什么】公开页面（首页 / 新闻 / 社团介绍 / 获奖展示）+ 自己的个人主页（/me、/me/profile、/me/awards）
     * 【不能看什么】任何后台页面（/admin/**）—— 一律 403
     *
     * 【怎么做到的】DbUserDetailsService 里对退社的人**一张业务角色牌都不发**
     *   （只发一个标记用的 ROLE_LEFT）→ SecurityConfig 里 /admin/** 的角色要求天然落空 → 自动 403。
     *   ⚠️ 所以**别**用"把他从登录白名单里去掉"来实现这条需求 —— 那样他连公开页面都看不了了。
     */
    LEFT("退社"),

    /** 毕业 —— 可以登录（项目负责人 2026-10-02 决定：毕业生能回来看自己的记录） */
    GRADUATED("毕业"),

    DISABLED("已禁用");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
