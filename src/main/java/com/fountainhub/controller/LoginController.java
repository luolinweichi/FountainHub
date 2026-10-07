package com.fountainhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 登录页控制器 —— 只负责「把登录页面渲染出来」。
 *
 * 【为什么登录处理不在这里】
 *   表单提交（POST /login）是由 Spring Security 的过滤器接管的，
 *   根本不经过 Controller。我们只需要提供 GET /login 这个页面。
 *   所以本类只有一个方法 —— 看起来"少得奇怪"，但这是正常的。
 *
 * 【为什么 SecurityConfig 里要写 .loginPage("/login")】
 *   不写的话，Spring 会用它自带的那个很朴素的默认登录页。
 *   写了之后，未登录的请求会被重定向到我们自己的页面。
 *
 * ⚠️ 【2026-10-04 修正：这里曾经写死过社团名】
 *   原来这里有这两行：
 *       model.addAttribute("systemName", "FountainHub");
 *       model.addAttribute("clubName", "……");   ← 写死的旧社名
 *   而系统名/社团名的**唯一来源**是 `config/GlobalModelAdvice`（@ControllerAdvice，全站统一注入）。
 *   Spring 的执行顺序是 **Controller 晚于 @ControllerAdvice** →
 *   这里写死的值会把 Advice 注入的值**覆盖**掉，
 *   结果就是**登录页一直显示旧社名**（T-043 改社名时漏掉了这一处，属静默失效）。
 * 🔄 开源脱敏（2026-10-04）：注释里原本写着真实社名的历史值，已改成省略号占位。
 *   👉 所以本方法现在**只返回视图名**，什么都不设：名字全部交给 GlobalModelAdvice。
 *      （这正是 T-043 在 HomeController 上做过的那次修正，当时漏了登录页。）
 */
@Controller
public class LoginController {

    /**
     * 渲染登录页。
     *
     * 返回的字符串 "login" 会去找 src/main/resources/templates/login.html
     * （和 HomeController 返回 "index" 是同一个规则）。
     */
    @GetMapping("/login")
    public String loginPage() {
        // ⚠️ 这里**故意不设 systemName / clubName** —— 见类注释：
        //    设了就会覆盖 GlobalModelAdvice 的正确值（旧社名 bug 的成因）。
        return "login";
    }

    // 🔄 R-004：原本放在这里的 /403 已经搬到 `ErrorPageController`。
    //    原因：403（授权失败）和登录（认证入口）是两件事，类名应该和职责对上。
    //    **URL 完全没有变化**，仍然是 /403。
}
