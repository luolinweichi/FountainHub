package com.fountainhub.controller;

import com.fountainhub.entity.PasswordResetRequest;
import com.fountainhub.entity.User;
import com.fountainhub.repository.PasswordResetRequestRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.security.PasswordResetReviewPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * 后台审核「忘记密码申请」（T-012B）。
 *
 * ===========================================================================
 * 🔒 【本类是整个功能最关键的地方】
 *   只有这里才允许写 users.password_hash —— 审核【通过】的那一刻。
 *   提交申请的 ForgotPasswordController 碰都不许碰它，否则审核就没意义了。
 *
 * 【谁能审核】按项目负责人 2026-10-04 的决定：
 *   指导老师 / 正社长 / **副社长** 都可以（副社长**可以**审核）。
 *   ⚠️ 组长（ROLE_LEADER）不行 —— 他连 /admin/** 都进不去。
 *
 * 【入口放在哪】按项目负责人要求：**不另做审核页**，
 *   就放在「入社申请」审核的同一个地方（/admin/students 列表里）。
 * ===========================================================================
 */
@Controller
@RequestMapping("/admin/students")
public class AdminPasswordResetController {

    private final PasswordResetRequestRepository resetRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AdminPasswordResetController(PasswordResetRequestRepository resetRepository,
                                        UserRepository userRepository,
                                        CurrentUserService currentUserService) {
        this.resetRepository = resetRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    /** 审核【通过】：把申请里的新哈希写回 users.password_hash —— 新密码从这一刻起生效 */
    @PostMapping("/reset/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String approve(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        // 🔒 三层审核要看"审核人是谁"，所以先把当前用户取出来（而不是拖到最后才算 reviewedBy）
        User me = currentUserService.require(authentication);

        PasswordResetRequest req = resetRepository.findById(id).orElse(null);
        if (req == null) {
            ra.addFlashAttribute("error", "这条申请不存在。");
            return "redirect:/admin/students";
        }
        // 幂等保护：已审核过的不重复处理（防止同事手快点两下）
        if (!PasswordResetRequest.PENDING.equals(req.getStatus())) {
            ra.addFlashAttribute("error", "这条申请已经审核过了。");
            return "redirect:/admin/students";
        }

        User user = userRepository.findById(req.getUserId()).orElse(null);
        if (user == null) {
            ra.addFlashAttribute("error", "申请人已经不在系统里了。");
            return "redirect:/admin/students";
        }

        // 🔒🔒【2026-10-07 三层审核 —— 这里才是真正的安全边界】
        //   @PreAuthorize 只保证"我是社长层"，**管不了"我能不能审这一条"**：
        //   申请人是谁要查了记录才知道，注解看不到 → 必须在这里再判一次。
        //   不通过就抛 AccessDeniedException → GlobalExceptionHandler 原样抛回 → 交给 Spring 渲染 403 页面。
        if (!PasswordResetReviewPolicy.canReview(me, user)) {
            throw new AccessDeniedException(PasswordResetReviewPolicy.denyReason(user));
        }

        // ⭐ 关键一行：到这里新密码才真正生效
        user.setPasswordHash(req.getNewPasswordHash());
        userRepository.save(user);

        req.setStatus(PasswordResetRequest.APPROVED);
        req.setReviewedBy(me.getId());
        req.setReviewedAt(LocalDateTime.now());
        resetRepository.save(req);

        ra.addFlashAttribute("saved", "已通过 " + user.getRealName() + " 的改密申请，新密码已生效。");
        return "redirect:/admin/students";
    }

    /** 审核【拒绝】：申请作废，**旧密码继续有效** */
    @PostMapping("/reset/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String reject(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        PasswordResetRequest req = resetRepository.findById(id).orElse(null);
        if (req == null || !PasswordResetRequest.PENDING.equals(req.getStatus())) {
            ra.addFlashAttribute("error", "这条申请不存在或已经审核过了。");
            return "redirect:/admin/students";
        }

        // 🔒 三层审核：拒绝也要判资格 —— 否则社长能拿「拒绝」去作废他本就无权过问的申请
        User applicant = userRepository.findById(req.getUserId()).orElse(null);
        if (!PasswordResetReviewPolicy.canReview(me, applicant)) {
            throw new AccessDeniedException(PasswordResetReviewPolicy.denyReason(applicant));
        }

        // 🔒 注意：这里【不动】users.password_hash —— 旧密码继续有效
        req.setStatus(PasswordResetRequest.REJECTED);
        req.setReviewedBy(me.getId());
        req.setReviewedAt(LocalDateTime.now());
        resetRepository.save(req);

        ra.addFlashAttribute("saved", "已拒绝这条改密申请，对方原来的密码仍然有效。");
        return "redirect:/admin/students";
    }
}
