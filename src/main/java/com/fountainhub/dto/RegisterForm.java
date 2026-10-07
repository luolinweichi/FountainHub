package com.fountainhub.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 注册表单的数据载体（R-010）。
 *
 * 【为什么要有它】
 *   原来 `refill()` 方法收 **13 个参数**，而且被调用了 7 次 ——
 *   加一个新字段就要改 8 个地方，非常容易漏。现在装进这个对象，只传 1 个参数。
 *
 * 【⚠️ 它只是一个"容器"，不是用来绑定请求的】
 *   Controller 里**仍然用 `@RequestParam` 逐个接收字段**，然后装进本对象。
 *   **绝不能**改成 `submit(@ModelAttribute RegisterForm form)` 那种整体绑定 ——
 *   那会引入**批量赋值漏洞**：攻击者能在表单里偷塞 `role=TEACHER` 之类的字段，
 *   Spring 会老实帮他绑上去。（CODE_MAP 第 9 节第 7 条铁律）
 *
 * 【为什么可以用 Lombok】AGENTS.md 第 6.1 节：DTO 允许 `@Getter/@Setter`（禁止 `@Data`）。
 */
@Getter
@Setter
public class RegisterForm {

    /** 真实姓名（必填） */
    private String realName;

    /** 学号（必填） */
    private String studentNo;

    /** 用户名（必填） */
    private String username;

    /** 手机号（必填，也是登录方式之一） */
    private String phone;

    /** 年级 */
    private String grade;

    /** 专业 / 班级 */
    private String majorClass;

    /** QQ */
    private String qq;

    /** 微信 */
    private String wechat;

    /** 邮箱 */
    private String email;

    /** 擅长的语言 */
    private String languages;

    /** 阶段（枚举名字符串，可能非法） */
    private String level;
}
