package com.fountainhub.controller;

import com.fountainhub.entity.StudyGroup;
import com.fountainhub.entity.User;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「我的小组」（T-017）—— 组长专用页面。
 *
 * ===========================================================================
 * 【这个页面的全部内容，就三样】
 *   组名 · 组员人数 · 组员姓名
 *
 *   **没有**学号、**没有**角色、**没有**状态、**没有**手机号。
 *
 * 【为什么单独开一个页面，而不是让组长去用学员列表】
 *   学员列表（/admin/students）天生要显示学号、角色、状态、操作按钮，
 *   还有审核 / 禁用 / 改角色等入口。要让组长"只能用其中一点点"，
 *   就得在那里到处加判断 —— 加漏一处就是越权，风险很高。
 *   单独开一个小页面，**从设计上就不可能泄漏**别的信息。
 *
 * 【权限规则（2026-10-02 项目负责人定，T-017）】
 *   · 组长（含"社长兼组长"）→ 看自己的组 ✅
 *   · **社长层但不带组**      → **重定向到 /admin/groups**（那里功能更全，给他更好的页面），**不是 403**
 *   · 其他（学员等）          → **403**
 *
 *   🔒 组长**不能修改任何数据** —— 所以本类**只有 GET，没有任何 POST**。
 * ===========================================================================
 */
@Controller
public class MyGroupController {

    private final StudyGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public MyGroupController(StudyGroupRepository groupRepository,
                             UserRepository userRepository,
                             CurrentUserService currentUserService) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me/group")
    public String myGroup(Authentication authentication, Model model) {
        User me = currentUserService.require(authentication);

        // 我带的组（可能 0 个、1 个、多个）
        List<StudyGroup> myGroups = groupRepository.findByLeaderId(me.getId());

        if (myGroups.isEmpty()) {
            // 不是组长。但"没带组"不等于"没权限" —— 要分清是谁：
            if (isStaff(authentication)) {
                // 社长层：权限最高，只是这个页面没那个全 → 送去功能更全的分组管理页
                return "redirect:/admin/groups";
            }
            // 其他人（学员）：确实没有任何组可看 → 403
            // 抛 AccessDeniedException 而不是自己 return 403 页面，
            //   这样由 Spring Security 统一处理，**HTTP 状态码是真正的 403**（不是 302 再 200）。
            throw new AccessDeniedException("只有组长能查看「我的小组」");
        }

        // 每个组的成员名单（只取姓名，页面也只会渲染姓名）
        Map<Long, List<User>> members = new LinkedHashMap<>();
        for (StudyGroup g : myGroups) {
            members.put(g.getId(), userRepository.findAllByGroupIdOrderByRealNameAsc(g.getId()));
        }

        model.addAttribute("myGroups", myGroups);
        model.addAttribute("members", members);
        return "me/group";
    }

    /** 是不是社长层（指导老师 / 正社长 / 副社长） */
    private boolean isStaff(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a ->
                "ROLE_TEACHER".equals(a.getAuthority())
                        || "ROLE_PRESIDENT".equals(a.getAuthority())
                        || "ROLE_VICE_PRESIDENT".equals(a.getAuthority()));
    }
}
