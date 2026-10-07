package com.fountainhub.util;

import com.fountainhub.entity.User;

/**
 * 「这个人叫什么」的小工具（T-033）。
 *
 * 【为什么要有它】
 *   同一段 4 行逻辑（有姓名用姓名、没有就退回用户名、账号没了给一句话）
 *   原先在 `AdminAwardController` 和 `MyAwardController` 里**各写了一份私有副本**，
 *   T-033 的公开页又要用第三次 —— 抄三遍就是三处将来要一起改的隐患。
 *   这和 R-006 把 `blankToNull` / `parseEnum` 收进 {@link FormUtils} 是同一个理由。
 *
 * ⚠️ **只返回姓名**，绝不返回手机号/学号（全站红线）。
 */
public final class UserDisplay {

    /** 工具类不允许被 new */
    private UserDisplay() {
    }

    /**
     * 显示名：优先 `real_name`，没有就退回 `username`，账号已删除给一句明确说明。
     *
     * 【为什么要有兜底】数据库里的用户可能被删（或关联指向了一个不存在的 id），
     *   页面上出现空白会让人以为"页面坏了"，不如写明「(账号已删除)」。
     */
    public static String name(User u) {
        if (u == null) {
            return "(账号已删除)";
        }
        String real = FormUtils.blankToNull(u.getRealName());
        return real != null ? real : u.getUsername();
    }
}
