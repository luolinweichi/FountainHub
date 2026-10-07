package com.fountainhub.controller;

import com.fountainhub.entity.User;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 个人中心 · **修改密码**（R-009 从 ProfileController 拆出来的，原 T-008）。
 *
 * **URL 完全没变**，还是 `/me/password`。
 *
 * 【🔒 安全铁律】
 *   当前用户**只能从会话取**（`authentication.getName()`），**绝不从 URL 取 id** ——
 *   否则坏人改一下地址栏的 id 就能改【别人】的密码。
 */
@Controller
@RequestMapping("/me")
public class PasswordController {

    /** 新密码最短长度。8 位是个不过分的要求 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public PasswordController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    /** 显示"修改密码"表单 */
    @GetMapping("/password")
    public String passwordForm(Authentication authentication, Model model) {
        // 🔒【2026-10-07】如果这个人是"被强制改密"的状态，页面上要说清楚 ——
        //   否则他会以为"点哪个链接都被弹回来 = 系统坏了"。
        currentUserService.find(authentication).ifPresent(u ->
                model.addAttribute("mustChange", Boolean.TRUE.equals(u.getMustChangePassword())));
        return "me/password";
    }

    /**
     * 处理"修改密码"提交。
     *
     * 四道校验，顺序是"由便宜到昂贵"：
     *   1) 旧密码对不对        —— 必须用 passwordEncoder.matches()，绝不自己写比较逻辑
     *   2) 新密码够不够长
     *   3) 两次新密码一不一致
     *   4) 新密码是不是和旧密码一样
     *
     * 【🔄 R-009 搬家说明】本方法逻辑与搬家前**完全一致**，一个字都没改，
     *   只把"取当前用户"改成了统一的 `CurrentUserService`（R-007）。
     */
    @PostMapping("/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 Model model) {

        // 从【会话】里取当前登录者 —— 不是从 URL 取，防止改别人密码
        User user = currentUserService.require(authentication);

        // ---- 1) 旧密码校验 ----
        // matches(明文, 数据库里的哈希)：把明文用同样的算法+盐算一遍再比对。
        // 绝不能写成 oldPassword.equals(user.getPasswordHash())，那样永远不相等。
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return fail(model, "旧密码不正确");
        }

        // ---- 2) 长度 ----
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            return fail(model, "新密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }

        // ---- 3) 两次输入一致 ----
        if (!newPassword.equals(confirmPassword)) {
            return fail(model, "两次输入的新密码不一致");
        }

        // ---- 4) 不能和旧密码相同 ----
        // 这里同样用 matches()，而不是比较明文 —— 因为我们手上只有哈希
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            return fail(model, "新密码不能和旧密码相同");
        }

        // ---- 通过：存新哈希（数据库里永远不出现明文）----
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // 🔒【2026-10-07】改完就解除"强制改密"标记 ——
        //   从下一次请求起，MustChangePasswordInterceptor 就不再拦他了。
        user.setMustChangePassword(false);
        userRepository.save(user);

        // ---- 改完密码强制重新登录 ----
        // 【为什么要这么做】密码变了，旧会话不该再有效；
        //   同时这也让你【立刻用新密码登录一次】，等于顺手验证了新密码生效。
        SecurityContextHolder.clearContext();          // 清掉当前请求里的认证信息
        request.getSession().invalidate();             // 让会话失效（认证信息就存在会话里）

        return "redirect:/login?passwordChanged";
    }

    /** 校验失败：把错误信息塞进 Model，回到同一个页面（不跳转，用户填的内容还在） */
    private String fail(Model model, String message) {
        model.addAttribute("error", message);
        return "me/password";
    }
}
