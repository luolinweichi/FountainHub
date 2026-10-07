package com.fountainhub.controller;

import com.fountainhub.entity.PasswordResetRequest;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.User;
import com.fountainhub.repository.PasswordResetRequestRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.FormUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * 忘记密码【申请】页（T-012B）。
 *
 * ===========================================================================
 * ⚠️ 【本功能的定性，务必守住】
 *   这是「**申请 + 审核**」，**不是「重置」**：
 *     · 学员自己填想设置的新密码 → 提交 → 状态 PENDING
 *     · **此时旧密码仍然有效**（因为新哈希只存在申请表里，没写回 users）
 *     · 社长审核【通过】后，才把新哈希写回 users.password_hash
 *     · 社长【拒绝】→ 申请作废，旧密码继续有效
 *
 *   🔒 所以本类【绝对不允许】碰 users.password_hash —— 那是审核通过后的事，
 *      在 AdminPasswordResetController 里做。
 *
 * 【页面上的用词】需求方明确要求：**不许出现「重置」二字**。
 * ===========================================================================
 */
@Controller
public class ForgotPasswordController {

    /** 新密码最短长度（与注册页保持一致） */
    private static final int MIN_PASSWORD = 8;

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final PasswordEncoder passwordEncoder;

    public ForgotPasswordController(UserRepository userRepository,
                                    PasswordResetRequestRepository resetRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/forgot")
    public String page(Model model) {
        model.addAttribute("done", false);
        return "forgot";
    }

    @PostMapping("/forgot")
    public String submit(@RequestParam(required = false) String account,
                         @RequestParam(required = false) String newPassword,
                         @RequestParam(required = false) String confirmPassword,
                         Model model) {

        String acc = FormUtils.trimToEmpty(account);
        model.addAttribute("account", acc);
        model.addAttribute("done", false);

        // ---------- ① 账号必须存在 ----------
        // 三种都能填：学号 / 用户名 / 手机号（和登录页一样的规则）
        Optional<User> found = findByAccount(acc);
        if (found.isEmpty()) {
            model.addAttribute("accountError", "没有找到这个账号，请检查学号 / 用户名 / 手机号。");
            return "forgot";
        }
        User user = found.get();

        // ---------- ①.5 🔒【2026-10-07 三层审核】指导老师的申请，这条路走不通 ----------
        //   按项目负责人决定：指导老师忘记密码**只能由维护人员直接改数据库**（见 README）。
        //   【为什么在这里就挡掉，而不是"让他提交、反正没人能审"】
        //     那种记录会**永远挂在待审核列表里**（按规则谁都审不了）→ 一条僵尸数据，纯添乱。
        if (user.getRole() == Role.TEACHER) {
            model.addAttribute("accountError", "这个账号的密码不能在这里申请修改，请直接联系系统维护人员处理。");
            return "forgot";
        }

        // ---------- ② 两次密码必须一致 ----------
        if (!FormUtils.trimToEmpty(newPassword).equals(FormUtils.trimToEmpty(confirmPassword))) {
            model.addAttribute("passwordError", "两次输入的新密码不一致。");
            return "forgot";
        }

        // ---------- ③ 长度 ----------
        if (FormUtils.trimToEmpty(newPassword).length() < MIN_PASSWORD) {
            model.addAttribute("passwordError", "新密码至少 " + MIN_PASSWORD + " 位。");
            return "forgot";
        }

        // ---------- ④ 把这个人旧的待审核申请作废（只保留最新一条）----------
        List<PasswordResetRequest> olds = resetRepository.findByUserIdAndStatus(user.getId(), PasswordResetRequest.PENDING);
        for (PasswordResetRequest old : olds) {
            old.setStatus(PasswordResetRequest.REJECTED);
            old.setReviewNote("学员提交了新的申请，旧申请自动作废");
            resetRepository.save(old);
        }

        // ---------- ⑤ 存新申请 ----------
        PasswordResetRequest req = new PasswordResetRequest();
        req.setUserId(user.getId());
        // 🔒 提交时就哈希，绝不存明文；但【只是存在申请表里】，不碰 users
        req.setNewPasswordHash(passwordEncoder.encode(FormUtils.trimToEmpty(newPassword)));
        req.setStatus(PasswordResetRequest.PENDING);
        resetRepository.save(req);

        model.addAttribute("done", true);
        model.addAttribute("doneName", user.getRealName());
        return "forgot";
    }

    /** 学号 → 用户名 → 手机号，找到就返回（和 DbUserDetailsService 的顺序一致） */
    private Optional<User> findByAccount(String account) {
        if (account.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> u = userRepository.findByUsername(account);
        if (u.isPresent()) { return u; }
        u = userRepository.findByStudentNo(account);
        if (u.isPresent()) { return u; }
        return userRepository.findByPhone(account);
    }
}
