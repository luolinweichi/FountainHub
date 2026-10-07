package com.fountainhub.controller;

import com.fountainhub.entity.StudentProfile;
import com.fountainhub.entity.User;
import com.fountainhub.repository.StudentProfileRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.util.FormUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 个人中心 · **我的资料**（R-009 拆分后的样子）。
 *
 * 【路径前缀 /me 的含义】
 *   /me/** 是「我自己」的页面：学员改自己的密码、看自己的资料……
 *   与 /admin/**（管理别人）相对。
 *   在 SecurityConfig 里，/me/** 只要求"已登录"，不要求管理员角色。
 *
 * 【🔄 R-009 拆分说明】
 *   本类原先还兼着「修改密码」（T-008）。一个类管两件不相干的事，现在拆开：
 *     `ProfileController`   → **本类**：/me/profile（我的资料）
 *     `PasswordController`  → /me/password（修改密码）
 *   **URL 一个都没变**，两个类共用同一个类级路径 `/me`。
 */
@Controller
@RequestMapping("/me")
public class ProfileController {

    private final StudentProfileRepository profileRepository;
    private final CurrentUserService currentUserService;

    public ProfileController(StudentProfileRepository profileRepository,
                             CurrentUserService currentUserService) {
        this.profileRepository = profileRepository;
        this.currentUserService = currentUserService;
    }

    /** 显示「我的资料」页面 */
    @GetMapping("/profile")
    public String profilePage(Authentication authentication, Model model) {
        // 🔄 R-007：取当前用户的写法统一收进 CurrentUserService
        User me = currentUserService.require(authentication);
        model.addAttribute("me", me);
        // 可能还没有档案（比如老师账号），模板里用 ?. 安全取值
        model.addAttribute("profile", profileRepository.findById(me.getId()).orElse(null));
        return "me/profile";
    }

    /**
     * 保存「我的资料」。
     *
     * 🔒 两条安全铁律：
     *   1. 从【会话】取当前用户，不用 URL 参数
     *      → 天然只能改自己 ——「改不了别人的资料」这条验收就是这么实现的。
     *   2. **只显式接收 3 个允许的字段**（qq / wechat / email）。
     *      绝不能写成 `@ModelAttribute StudentProfile` 那种"整体绑定" ——
     *      攻击者能在表单里偷塞别的参数，Spring 会老实帮他绑上去（**批量赋值漏洞**）。
     *
     * 🔒 手机号【故意不在这里】：它是登录凭据（三种登录方式之一），
     *    与姓名、学号一样，只能由管理员在 /admin/students/{id} 改。
     */
    @PostMapping("/profile")
    public String saveProfile(@RequestParam(required = false) String qq,
                              @RequestParam(required = false) String wechat,
                              @RequestParam(required = false) String email,
                              Authentication authentication,
                              RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        // 没有档案就顺手建一条（老师/社长第一次进这个页面时会发生）
        StudentProfile profile = profileRepository.findById(me.getId()).orElseGet(() -> {
            StudentProfile created = new StudentProfile();
            created.setUserId(me.getId());
            return created;
        });

        profile.setQq(FormUtils.blankToNull(qq));
        profile.setWechat(FormUtils.blankToNull(wechat));
        profile.setEmail(FormUtils.blankToNull(email));
        profileRepository.save(profile);

        ra.addFlashAttribute("saved", "联系方式已保存");
        return "redirect:/me/profile";
    }
}
