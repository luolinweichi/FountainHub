package com.fountainhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 错误页面控制器（R-004 从 LoginController 搬过来的）。
 *
 * 【为什么单独一个类】
 *   403「没有权限」和「登录」是两件事：一个是授权失败，一个是认证入口。
 *   之前它挂在 LoginController 里，类名和职责对不上。搬到这里之后：
 *     LoginController       → 只管 /login
 *     ErrorPageController   → 只管错误页
 *   **URL 完全没变**，还是 /403。
 */
@Controller
public class ErrorPageController {

    /**
     * 「你没有权限」页面（HTTP 403）。
     *
     * 【什么时候会看到它】
     *   已登录、但角色不够。比如学员登录后访问 /admin/students。
     *   注意和"未登录"区分开：未登录会被送去登录页，不会看到这里。
     *
     * 【⚠️ 为什么用 `@RequestMapping` 而不是 `@GetMapping`（2026-10-02 踩过的坑）】
     *   `@PreAuthorize` 拒绝访问时，Spring Security 是用【转发 forward】跳到这里来的，
     *   而且【保留了原来的 HTTP 方法】。
     *   也就是说：一个被拒绝的 POST 请求，会以 POST 方法转发到 /403。
     *   如果这里只写 `@GetMapping`，转发过来就变成"POST 不被支持" → **405**（而不是 403）。
     *   用 `@RequestMapping`（不限方法）就没这个问题。**改回去会重现这个 bug。**
     */
    @RequestMapping("/403")   // ⚠️ 故意用 @RequestMapping，不用 @GetMapping
    public String forbidden() {
        return "403";
    }
}
