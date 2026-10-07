package com.fountainhub.controller;

import com.fountainhub.entity.Role;
import com.fountainhub.entity.UserStatus;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.FormUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台 · 学员**账号管理**（R-008 从 AdminStudentController 拆出来的）。
 *
 * 【为什么单独一个类】
 *   AdminStudentController 原先有 328 行、管了 6 件事（列表 / 详情 / 编辑 / 状态 / 角色 / 审核），
 *   是全项目最大的文件。现在按职责一分为二：
 *     `AdminStudentController`          → 看和改资料（列表 / 详情 / 编辑）
 *     `AdminStudentAccountController`   → 管账号（审核 / 改状态 / 改角色）
 *
 *   **所有 URL 一个都没变**，两个类共用同一个类级路径 `/admin/students`。
 *
 * 【🔒 权限（最敏感的部分，搬家时一字未改）】
 *   /admin/** 在 SecurityConfig 里限定为「指导老师 / 正社长 / 副社长」；
 *   而下面「改状态 / 改角色」两个接口**额外**要求 `@PreAuthorize`：
 *   只有 **指导老师 和 正社长** 能做，副社长调用会得到 403。
 *   依据：权限矩阵 ——「副社长不能管理账号」。
 */
@Controller
@RequestMapping("/admin/students")
public class AdminStudentAccountController {

    private final UserRepository userRepository;

    public AdminStudentAccountController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 修改账号状态（在读 / 待审核 / 退社 / 毕业 / 已禁用）。
     *
     * 🔒 `@PreAuthorize` = 只有指导老师和【正】社长能做。
     *    副社长调用会得到 403（走我们自己的 403 页面）。
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String changeStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes ra) {
        UserStatus target = FormUtils.parseEnum(UserStatus.class, status);
        if (target == null) {
            return back(id, ra, "状态值不合法");
        }
        userRepository.findById(id).ifPresent(u -> {
            u.setStatus(target);
            userRepository.save(u);
        });
        ra.addFlashAttribute("saved", "状态已更新为「" + target.getLabel() + "」");
        return "redirect:/admin/students/" + id;
    }

    /**
     * 调整角色。
     *
     * 🔒 同样只有指导老师和【正】社长能做 —— 改角色等于发权限，是最敏感的操作之一。
     */
    @PostMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String changeRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes ra) {
        Role target = FormUtils.parseEnum(Role.class, role);
        if (target == null) {
            return back(id, ra, "角色值不合法");
        }
        userRepository.findById(id).ifPresent(u -> {
            u.setRole(target);
            userRepository.save(u);
        });
        ra.addFlashAttribute("saved", "角色已调整为「" + target.getLabel() + "」");
        return "redirect:/admin/students/" + id;
    }

    /**
     * 审核通过：把「待审核」的人转为「在读」。
     *
     * 【为什么只处理 PENDING】
     *   加一道判断，防止有人拿这个接口去改一个已经是 ACTIVE 的账号（越权操作面越小越好）。
     *   不是待审核的，直接忽略，不做任何事。
     */
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            if (user.getStatus() == UserStatus.PENDING) {
                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);
            }
        });
        // 审完回到"待审核"筛选视图，方便连着审下一条
        return "redirect:/admin/students?status=PENDING";
    }

    /**
     * 审核拒绝：直接删掉这条申请。
     *
     * 【为什么是删除而不是标记禁用】
     *   待审核的账号还没产生任何数据（没有打卡、没有获奖），删掉最干净。
     *   数据库的外键是 ON DELETE CASCADE，所以它的档案行会一起消失。
     *
     * 同样只允许操作 PENDING —— 绝不能拿这个接口删掉一个正式学员。
     */
    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            if (user.getStatus() == UserStatus.PENDING) {
                userRepository.delete(user);
            }
        });
        return "redirect:/admin/students?status=PENDING";
    }

    /** 带一条错误信息回到详情页（原先在 AdminStudentController 里，拆分后两边各留一份小工具） */
    private String back(Long id, RedirectAttributes ra, String message) {
        ra.addFlashAttribute("error", message);
        return "redirect:/admin/students/" + id;
    }
}
