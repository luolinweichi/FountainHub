package com.fountainhub.controller;

import com.fountainhub.entity.Role;
import com.fountainhub.entity.StudyGroup;
import com.fountainhub.entity.User;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.FormUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台 · **分组管理**（T-016）。
 *
 * 【这个页面能做什么】
 *   · 看所有小组（组名 / 说明 / 组长 / 组员人数 / 排序）
 *   · 新建组、改组名与说明与排序
 *   · **指定组长**（也可以"取消组长"）
 *   · **添加组员**（2026-10-04 新增，见 addMember()）
 *   · **删除组**（2026-10-04 新增，见 delete()）
 *
 * 【🔒 权限：两类操作分开管】
 *   · **看**组列表 —— /admin/** 的规则已经管住了（指导老师 / 正社长 / 副社长都能看）
 *   · **改**（新建组 / 编辑组 / 指定组长 / 添加组员 / 删除组）—— 额外要求 `@PreAuthorize`：
 *     只有【指导老师 + 正社长】
 *
 *   依据：权限矩阵里「副社长**不能管理账号**」。**指定组长等于发身份**，和"改角色"同级，
 *   所以用和 R-008 那套完全一样的限制方式。
 *
 * 【关于"组长的角色"（方案 B，2026-10-02 项目负责人定）】
 *   指定组长时**不改任何人的 users.role** —— 只是把 `study_groups.leader_user_id` 设成他。
 *   他下次登录时，`DbUserDetailsService` 会自动给他多一个 `ROLE_LEADER`。
 *   这样"组长"这个身份**只有一份真相**（就在这张表里），永远不会和 users.role 打架。
 *
 * 【"人在哪个组"的唯一真相】
 *   只存在 `users.group_id` 这一个字段上。本页的「添加组员」与学员详情页的「所属组」下拉，
 *   **改的是同一个字段** —— 没有第二份数据，不会出现两边不一致。
 */
@Controller
@RequestMapping("/admin/groups")
public class AdminGroupController {

    private final StudyGroupRepository groupRepository;
    private final UserRepository userRepository;

    public AdminGroupController(StudyGroupRepository groupRepository,
                                UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    /** 组列表 */
    @GetMapping
    public String list(Model model) {
        List<StudyGroup> groups = groupRepository.findAllByOrderBySortOrderAsc();

        // 每个组的人数。组数是个位数，这里一条条数完全没问题。
        Map<Long, Long> memberCounts = new LinkedHashMap<>();
        for (StudyGroup g : groups) {
            memberCounts.put(g.getId(), userRepository.countByGroupId(g.getId()));
        }

        List<User> allUsers = userRepository.findAllByOrderByRealNameAsc();

        // 「指定组长」的下拉：列**全部人** —— 社长兼组长是常见情况，老师也可能带组。
        // 🔒 只显示姓名 —— 下拉框里**绝不出现手机号**（红线）
        model.addAttribute("allUsers", allUsers);

        // 「添加组员」的下拉：**只列学员**（把老师/社长塞进"学习小组"没有意义）。
        //   数据量小（几十人），在内存里筛一下就够了，不必为此加一个 Repository 方法。
        List<User> memberCandidates = new ArrayList<>();
        for (User u : allUsers) {
            if (u.getRole() == Role.STUDENT) {
                memberCandidates.add(u);
            }
        }
        model.addAttribute("memberCandidates", memberCandidates);

        // 组 id → 组名。给「添加组员」的下拉用：显示"他现在在哪个组"，
        //   操作者才知道加入本组后他会**从哪个组移出**。
        Map<Long, String> groupNames = new LinkedHashMap<>();
        for (StudyGroup g : groups) {
            groupNames.put(g.getId(), g.getName());
        }
        model.addAttribute("groupNames", groupNames);

        model.addAttribute("groups", groups);
        model.addAttribute("memberCounts", memberCounts);
        return "admin/groups";
    }

    /**
     * 新建一个组。
     *
     * 【为什么先查重再保存】数据库的 name 列有 UNIQUE 约束，
     * 直接撞上去会抛异常变成 500；提前查一遍能给一句人话提示。
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sortOrder,
                         RedirectAttributes ra) {
        String groupName = FormUtils.trimToEmpty(name);
        if (groupName.isEmpty()) {
            return fail(ra, "组名不能为空");
        }
        if (groupRepository.existsByName(groupName)) {
            return fail(ra, "已经有一个叫「" + groupName + "」的组了");
        }

        StudyGroup group = new StudyGroup();
        group.setName(groupName);
        group.setDescription(FormUtils.blankToNull(description));
        group.setSortOrder(parseSortOrder(sortOrder));
        groupRepository.save(group);

        ra.addFlashAttribute("saved", "已新建小组「" + groupName + "」");
        return "redirect:/admin/groups";
    }

    /** 改组名 / 说明 / 排序 */
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sortOrder,
                         RedirectAttributes ra) {
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return fail(ra, "这个组不存在");
        }

        String groupName = FormUtils.trimToEmpty(name);
        if (groupName.isEmpty()) {
            return fail(ra, "组名不能为空");
        }
        // 查重时排除自己，否则"只改说明不改名"会误报重名
        if (!groupName.equals(group.getName()) && groupRepository.existsByName(groupName)) {
            return fail(ra, "已经有一个叫「" + groupName + "」的组了");
        }

        group.setName(groupName);
        group.setDescription(FormUtils.blankToNull(description));
        group.setSortOrder(parseSortOrder(sortOrder));
        groupRepository.save(group);

        ra.addFlashAttribute("saved", "「" + groupName + "」已保存");
        return "redirect:/admin/groups";
    }

    /**
     * 指定组长（也可以"取消组长"）。
     *
     * 【怎么实现的（方案 B）】只改 `study_groups.leader_user_id` 这一个字段。
     *   被指定的人**主角色一点不变**；他登录时会自动多一个 ROLE_LEADER。
     *   传空值 = 取消组长 → 那个人的 ROLE_LEADER 自动消失。
     */
    @PostMapping("/{id}/leader")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String setLeader(@PathVariable Long id,
                            @RequestParam(required = false) String leaderUserId,
                            RedirectAttributes ra) {
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return fail(ra, "这个组不存在");
        }

        Long newLeaderId = FormUtils.parseLong(leaderUserId);

        if (newLeaderId == null) {
            group.setLeader(null);
            groupRepository.save(group);
            ra.addFlashAttribute("saved", "「" + group.getName() + "」已取消组长");
            return "redirect:/admin/groups";
        }

        var newLeader = userRepository.findById(newLeaderId).orElse(null);
        if (newLeader == null) {
            return fail(ra, "找不到这个人");
        }

        group.setLeader(newLeader);
        groupRepository.save(group);

        // ---- 顺手把他加入这个组（2026-10-02 项目负责人确认要这么做）----
        // 【为什么】组长当然应该是自己组的人。否则会出现"他带着 Python 组，
        //   但学员列表里他的「组」却写着 C 语言组"这种自相矛盾的情况。
        // 【注意】如果他原本在别的组，这里会**把他移过来** —— 这正是"保证组长在自己带的组里"的意思。
        // 【对比】取消组长时**不会**把他移出组 —— 他仍然是这个组的组员，只是不再是组长。
        if (!group.getId().equals(newLeader.getGroupId())) {
            newLeader.setGroupId(group.getId());
            userRepository.save(newLeader);
        }

        ra.addFlashAttribute("saved",
                "「" + group.getName() + "」的组长已改为 " + newLeader.getRealName() + "（并已加入该组）");
        return "redirect:/admin/groups";
    }

    /**
     * 把一名学员加进这个组（2026-10-04 新增）。
     *
     * =======================================================================
     * 【为什么要加这个接口】
     *   在这之前，"设置人在哪个组"的**唯一入口**在**学员详情页**（`/admin/students/{id}`
     *   的「所属组」下拉）。但社长的自然动线是：**进分组管理 → 建组 → 往里面加人** ——
     *   逼他跑回学员列表一个个点，很不顺手。所以在这里补一个入口。
     *
     *   注意：**数据仍然只改 `users.group_id` 一个字段**，
     *   和学员详情页那个下拉、以及「指定组长会顺带把他移入该组」是**同一套逻辑** ——
     *   没有第二份真相，不会出现"两个页面对不上"。
     *
     * ⚠️【一个人只能在一个组】
     *   `users.group_id` 是**单值列**，所以"加入本组"天然意味着**从原来的组移出**。
     *   页面上下拉框会显示"他当前在哪个组"，提示语也会写明这一点 ——
     *   免得社长以为人是"多组共存"，回头发现原来的组少人了还不知道为什么。
     * =======================================================================
     */
    @PostMapping("/{id}/member")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String addMember(@PathVariable Long id,
                            @RequestParam(required = false) String userId,
                            RedirectAttributes ra) {
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return fail(ra, "这个组不存在（可能已经被删掉了）");
        }

        Long memberId = FormUtils.parseLong(userId);
        if (memberId == null) {
            return fail(ra, "请先在下拉框里选一名学员");
        }

        User member = userRepository.findById(memberId).orElse(null);
        if (member == null) {
            return fail(ra, "找不到这名学员");
        }

        // 已经在这个组里：不写库、也不给"已加入"这种假提示
        if (group.getId().equals(member.getGroupId())) {
            return fail(ra, "「" + member.getRealName() + "」已经在这个组里了");
        }

        // 先记下他原来在哪个组（改完就查不到了），用于提示"从哪移出"
        String previousGroupName = null;
        if (member.getGroupId() != null) {
            previousGroupName = groupRepository.findById(member.getGroupId())
                    .map(StudyGroup::getName)
                    .orElse(null);
        }

        member.setGroupId(group.getId());
        userRepository.save(member);

        ra.addFlashAttribute("saved",
                "已把「" + member.getRealName() + "」加入「" + group.getName() + "」"
                        + (previousGroupName == null ? "" : "（已从原组「" + previousGroupName + "」移出）"));
        return "redirect:/admin/groups";
    }

    /**
     * 删除一个组（2026-10-04 新增）。
     *
     * =======================================================================
     * ⭐【数据安全：删组不会"丢人" —— 这是数据库帮我们兜住的，不用写代码】
     *
     *   `users.group_id` 上的外键是这么建的（见 sql/01-schema.sql）：
     *       CONSTRAINT fk_users_group FOREIGN KEY (group_id)
     *           REFERENCES study_groups (id) ON DELETE SET NULL
     *                                          ^^^^^^^^^^^^^^^^^^^
     *   `ON DELETE SET NULL` 的意思是：**组被删掉时，数据库自动把组员的 group_id 置成 NULL**
     *   → 那些人变成「未分组」，**既不会报外键错，也绝不会连人一起删掉**。
     *
     *   所以这里**故意不写**"先把组员移出去"的补偿代码 ——
     *   手写补偿容易漏（比如漏了组长、漏了某个状态的人），交给数据库一条约束反而更稳。
     *
     * 【组长会怎样】组长的身份是**派生**的（存在 `study_groups.leader_user_id` 上）：
     *   组没了 → 这个身份自然消失，他本人、他的主角色一点变化都没有。
     *
     * 【🔒 权限】与新建/编辑同一条线：只有【指导老师 + 正社长】。副社长调用会得到 403。
     * =======================================================================
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return fail(ra, "这个组不存在（可能已经被删掉了）");
        }

        // 先记下来：删完就查不到了，而提示里要告诉用户"几个人变成了未分组"
        String groupName = group.getName();
        long memberCount = userRepository.countByGroupId(id);

        groupRepository.delete(group);

        ra.addFlashAttribute("saved",
                "已删除小组「" + groupName + "」"
                        + (memberCount > 0 ? "（" + memberCount + " 名组员已变为未分组）" : ""));
        return "redirect:/admin/groups";
    }

    // ---------------- 本类内部小工具 ----------------

    /** 排序值：没填或非法都当 0（不抛错，跟 parseStatus 一个思路） */
    private static int parseSortOrder(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 带一条错误信息回到列表页 */
    private String fail(RedirectAttributes ra, String message) {
        ra.addFlashAttribute("error", message);
        return "redirect:/admin/groups";
    }
}
