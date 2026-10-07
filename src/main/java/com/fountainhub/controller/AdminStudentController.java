package com.fountainhub.controller;

import com.fountainhub.entity.Level;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.StudentProfile;
import com.fountainhub.entity.StudyGroup;
import com.fountainhub.entity.User;
import com.fountainhub.entity.UserStatus;
import com.fountainhub.repository.StudentProfileRepository;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.entity.PasswordResetRequest;
import com.fountainhub.repository.PasswordResetRequestRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.repository.UserSpecifications;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.security.PasswordResetReviewPolicy;
import org.springframework.security.core.Authentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fountainhub.util.FormUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台 · 学员**列表与资料**。
 *
 * 【权限】
 *   /admin/** 在 SecurityConfig 里已经限定为「指导老师 / 正社长 / 副社长」，
 *   本类不需要再写任何权限判断 —— 学员访问会被 T-007 的规则拦成 403。
 *
 * 【🔄 R-008 拆分说明】
 *   本类原先有 328 行、管了 6 件事。现在拆成两个：
 *     `AdminStudentController`          → **本类**：列表 / 详情 / 编辑资料
 *     `AdminStudentAccountController`   → 审核 / 改状态 / 改角色（那些是"管账号"，权限更严）
 *   **URL 一个都没变。**
 */
@Controller
@RequestMapping("/admin/students")
public class AdminStudentController {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页条数上限。
     * 【为什么要有上限】分页参数是 URL 里传来的，别人可以随手改成 size=999999，
     *   一下子把整张表读进内存。给个硬上限是廉价的自我保护。
     */
    private static final int MAX_PAGE_SIZE = 50;

    /** 与服务端校验保持一致：初始密码最短长度（与自助注册同一条规则） */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final StudentProfileRepository profileRepository;
    /** T-016：列表页要显示「组」列、要提供「按组筛选」，所以需要组仓库 */
    private final StudyGroupRepository groupRepository;
    /** T-045：手工补录时要给初始密码加密（BCrypt），所以需要编码器 */
    private final PasswordEncoder passwordEncoder;
    /** 2026-10-07 三层审核：列表页要判断"我能不能审这条申请"，所以需要当前用户 */
    private final CurrentUserService currentUserService;

    public AdminStudentController(UserRepository userRepository,
                                   PasswordResetRequestRepository resetRepository,
                                  StudentProfileRepository profileRepository,
                                  StudyGroupRepository groupRepository,
                                  PasswordEncoder passwordEncoder,
                                  CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.profileRepository = profileRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    /**
     * 学员列表页。
     *
     * @param kw     关键词（匹配姓名或学号），可为空
     * @param status 状态筛选，可为空（空 = 全部）
     * @param page   页码，从 0 开始（Spring Data 的约定）
     * @param size   每页条数
     */
    @GetMapping
    public String list(@RequestParam(required = false) String kw,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String groupId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                       Authentication authentication,
                       Model model) {

        // ---- 1) 先把手里的参数"消毒" ----
        // 页码不能是负数；每页条数必须在 1 ~ 50 之间
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // 状态是字符串进来的，这里自己转成枚举。
        // 【为什么不直接写成 @RequestParam UserStatus status】
        //   那样一旦 URL 里的值不合法（比如 ?status=XXX），Spring 会直接抛 400 错误页，
        //   用户看到的是一个很难懂的错误。自己转的话，非法值就当"全部"处理，容错更好。
        UserStatus statusEnum = FormUtils.parseEnum(UserStatus.class, status);

        // T-016：组筛选。用 parseLong 而不是直接写 @RequestParam Long ——
        //   下拉框选「全部」时提交上来的是空字符串，直接转 Long 会抛 400 错误页。
        Long groupIdValue = FormUtils.parseLong(groupId);

        // ---- 2) 拼装查询条件 ----
        // Specification.allOf(...) 会把多个条件用 AND 连起来，其中 null 的会被忽略
        Specification<User> spec = Specification.allOf(
                UserSpecifications.keyword(kw),
                UserSpecifications.status(statusEnum),
                UserSpecifications.group(groupIdValue));   // T-016 新增

        // ---- 3) 分页 + 排序 ----
        // 按 id 升序：新加的人排在后面，顺序稳定（不排序的话数据库返回顺序不保证）
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));
        Page<User> userPage = userRepository.findAll(spec, pageable);

        // ---- 4) 交给页面 ----
        model.addAttribute("userPage", userPage);
        model.addAttribute("kw", kw);
        model.addAttribute("status", status);            // 保留原始字符串，供分页链接使用
        model.addAttribute("allStatuses", UserStatus.values());   // 状态下拉框选项
        model.addAttribute("allGroups", groupRepository.findAllByOrderBySortOrderAsc());  // T-016 组下拉框
        model.addAttribute("groupId", groupId);                   // 保留原始值，供分页链接使用
        model.addAttribute("groupNames", groupNamesById());        // T-016 组列：id 到组名的映射
        // T-012B：待审核的改密申请（放在「入社申请」审核的同一页，按需求方要求不另做页面）
        // 【2026-10-07 三层审核】原来只把申请本身丢给页面 —— 页面上只能显示一个 userId 数字，
        //   审核的人**根本不知道在审谁**，更没法判断自己该不该审。
        //   现在每条额外带上：申请人姓名 + 角色 + 「我能不能审」。
        //   规则在 PasswordResetReviewPolicy（和真正执行审核的 AdminPasswordResetController 共用同一份）。
        User me = currentUserService.require(authentication);
        List<PasswordResetRequest> pendingResets =
                resetRepository.findByStatusOrderByCreatedAtDesc(PasswordResetRequest.PENDING);
        // 一次把涉及的申请人都查出来（避免逐条查库；待审申请通常只有 0~2 条）
        List<Long> applicantIds = new ArrayList<>();
        for (PasswordResetRequest r : pendingResets) {
            applicantIds.add(r.getUserId());
        }
        Map<Long, User> applicantById = new HashMap<>();
        for (User u : userRepository.findAllById(applicantIds)) {
            applicantById.put(u.getId(), u);
        }
        List<Map<String, Object>> resetRows = new ArrayList<>();
        for (PasswordResetRequest r : pendingResets) {
            User applicant = applicantById.get(r.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("req", r);
            row.put("applicantName", applicant == null ? "（已不在系统）" : applicant.getRealName());
            row.put("applicantRole", applicant == null || applicant.getRole() == null
                    ? "" : applicant.getRole().getLabel());
            row.put("canReview", PasswordResetReviewPolicy.canReview(me, applicant));
            row.put("sqlOnly", PasswordResetReviewPolicy.isSqlOnly(applicant));
            resetRows.add(row);
        }
        model.addAttribute("resetRows", resetRows);

        return "admin/students";
    }

    /** 学员详情页 */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return renderDetail(id, model);
    }

    // ==================================================================================
    // T-045 · 手工补录学员（后台建号 + 建档）
    // ==================================================================================

    /**
     * 补录表单页。
     *
     * ⚠️【路由优先级，实测过】本类还有 `@GetMapping("/{id}")`，它**也能匹配** `/new`；
     *   但 Spring 的路径匹配**字面量优先于变量**（`/new` 比 `/{id}` 更具体）→ 命中这里。
     *   （若写成 `/{id}` 先匹配，`Long id` 转换 "new" 会直接 400 —— 已实测是 200，确认无此问题。）
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("allLevels", Level.values());
        model.addAttribute("allGroups", groupRepository.findAllByOrderBySortOrderAsc());
        return "admin/student-new";
    }

    /**
     * 提交补录：**一次建账号 + 建档案**。
     *
     * ===========================================================================
     * 【与自助注册（RegisterController）的异同 —— 别照抄错】
     *   相同：角色**一律 `STUDENT`**（红线，绝不允许在这里建老师/社长账号）；
     *         密码**存 BCrypt 哈希**，绝不存明文；逐个 `@RequestParam`（防批量赋值漏洞）。
     *   不同：① 状态**直接 `ACTIVE`（在读）** —— 管理员主动建的，不再走"待审核"（T-045 已定）；
     *         ② 初始密码由**管理员填**，建完要把「账号 + 初始密码」告诉本人。
     *
     * 【为什么加 @Transactional】一次写两张表（users / student_profiles），
     *   不能留下"有账号没档案"的半成品 —— 与 RegisterController 同一个理由。
     *
     * 【出错为什么直接渲染页面、而不是重定向】要把填过的内容**原样带回**，
     *   免得管理员重打一遍长表单（与 RegisterController.refill 一致）。
     *   ⚠️ **密码不回显**（安全）。
     * ===========================================================================
     */
    @PostMapping
    @Transactional
    public String create(@RequestParam String realName,
                         @RequestParam String studentNo,
                         @RequestParam String username,
                         @RequestParam String phone,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         @RequestParam(required = false) String grade,
                         @RequestParam(required = false) String majorClass,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String level,
                         @RequestParam(required = false) String groupId,
                         Model model,
                         RedirectAttributes ra) {

        // 去掉首尾空格（从别处复制粘贴很容易带上空格）
        realName = FormUtils.trimToEmpty(realName);
        studentNo = FormUtils.trimToEmpty(studentNo);
        username = FormUtils.trimToEmpty(username);
        phone = FormUtils.trimToEmpty(phone);

        // 出错时要回填的内容，先装好（⚠️ 故意不含密码）
        Map<String, String> filled = new LinkedHashMap<>();
        filled.put("realName", realName);
        filled.put("studentNo", studentNo);
        filled.put("username", username);
        filled.put("phone", phone);
        filled.put("grade", grade);
        filled.put("majorClass", majorClass);
        filled.put("languages", languages);
        filled.put("level", level);
        filled.put("groupId", groupId);

        // ---------- 校验 ----------
        if (realName.isEmpty())  return refillNew(model, "请填写真实姓名", filled);
        if (studentNo.isEmpty()) return refillNew(model, "请填写学号", filled);
        if (username.isEmpty())  return refillNew(model, "请填写用户名", filled);
        if (!phone.matches("1\\d{10}")) return refillNew(model, "手机号格式不对（应为 11 位、以 1 开头）", filled);
        if (password == null || password.length() < MIN_PASSWORD_LENGTH)
            return refillNew(model, "初始密码至少 " + MIN_PASSWORD_LENGTH + " 位", filled);
        if (!password.equals(confirmPassword))
            return refillNew(model, "两次输入的密码不一致", filled);

        // ---------- 唯一性（库里三列本来就有 UNIQUE，提前查是为了给人话提示，而不是 500）----------
        if (userRepository.findByUsername(username).isPresent())
            return refillNew(model, "这个用户名已经有人用了，换一个吧", filled);
        if (userRepository.findByStudentNo(studentNo).isPresent())
            return refillNew(model, "这个学号已经有人用了", filled);
        if (userRepository.findByPhone(phone).isPresent())
            return refillNew(model, "这个手机号已经有人用了", filled);

        // ---------- 建账号 ----------
        User user = new User();
        user.setUsername(username);
        user.setStudentNo(studentNo);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));   // 存哈希，绝不存明文
        user.setRealName(realName);
        user.setRole(Role.STUDENT);            // 🔒 红线：这里只能建学员
        user.setLogisticsDuty(false);
        user.setStatus(UserStatus.ACTIVE);     // ⭐ T-045 已定：管理员补录 = 直接在读
        user.setGroupId(FormUtils.parseLong(groupId));
        userRepository.save(user);             // IDENTITY 主键 → save 之后 id 已被回填

        // ---------- 建档案（主键 = 同一个 id）----------
        StudentProfile profile = new StudentProfile();
        profile.setUserId(user.getId());
        profile.setGrade(FormUtils.blankToNull(grade));
        profile.setMajorClass(FormUtils.blankToNull(majorClass));
        profile.setLanguages(FormUtils.blankToNull(languages));
        profile.setLevel(FormUtils.parseEnum(Level.class, level));
        profile.setJoinDate(LocalDate.now());  // 入社日期记成补录当天
        profileRepository.save(profile);

        // 建完跳到这名学员的详情页，并提示管理员"记得把账号和初始密码告诉本人"
        ra.addFlashAttribute("saved", "已创建学员「" + realName + "」（用户名 " + username
                + "）。请把【账号 + 初始密码】告诉本人，让他到「我的 → 修改密码」改成自己的密码。");
        return "redirect:/admin/students/" + user.getId();
    }

    /**
     * 补录表单校验失败 → 带错误信息 + 填过的内容回到表单页。
     *
     * 【🔄 为什么收一个 Map 而不是 11 个参数】`RegisterController.refill()` 原来就是
     *   13 个参数、被调 9 次（R-010 后来改成传对象）。这里**从一开始就不重复那个坑**。
     */
    private String refillNew(Model model, String error, Map<String, String> filled) {
        model.addAttribute("error", error);
        filled.forEach((k, v) -> model.addAttribute(k, v));
        model.addAttribute("allLevels", Level.values());
        model.addAttribute("allGroups", groupRepository.findAllByOrderBySortOrderAsc());
        return "admin/student-new";
    }

    /**
     * 保存编辑（账号字段 + 档案字段）。
     *
     * 【谁能用】所有能进 /admin/** 的人（指导老师 / 正社长 / 副社长）都能改资料。
     *   —— 权限矩阵里说的是「副社长不能删除数据、不能管理账号」，
     *      这里改的是"资料"，而「改角色、禁用账号」才算"管账号"，
     *      那两件事在 `AdminStudentAccountController` 里做了更严的限制。
     *
     * 【出错怎么办】用 flash 错误信息 + 重定向回详情页。
     *   为什么不直接渲染页面？因为那样刷新会让浏览器提示"是否重新提交表单"，
     *   重定向（POST-Redirect-Get）能避免这个烦人的提示。
     */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String realName,
                         @RequestParam String studentNo,
                         @RequestParam String username,
                         @RequestParam(required = false) String phone,
                         @RequestParam(required = false) String grade,
                         @RequestParam(required = false) String majorClass,
                         @RequestParam(required = false) String qq,
                         @RequestParam(required = false) String wechat,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String level,
                         @RequestParam(required = false) String groupId,   // T-016：所属组
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDate,
                         RedirectAttributes ra) {

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return "redirect:/admin/students";
        }

        realName = realName.trim();
        studentNo = studentNo.trim();
        username = username.trim();
        phone = FormUtils.blankToNull(phone);

        if (realName.isEmpty()) return back(id, ra, "真实姓名不能为空");
        if (studentNo.isEmpty()) return back(id, ra, "学号不能为空");
        if (username.isEmpty()) return back(id, ra, "用户名不能为空");
        if (phone != null && !phone.matches("1\\d{10}")) return back(id, ra, "手机号格式不对（11 位、以 1 开头）");

        // ---- 唯一性校验：注意要排除【自己】，否则改别的字段时会误报"已被占用" ----
        if (userRepository.findByUsername(username).filter(u -> !u.getId().equals(id)).isPresent())
            return back(id, ra, "用户名已被别人使用");
        if (userRepository.findByStudentNo(studentNo).filter(u -> !u.getId().equals(id)).isPresent())
            return back(id, ra, "学号已被别人使用");
        if (phone != null && userRepository.findByPhone(phone).filter(u -> !u.getId().equals(id)).isPresent())
            return back(id, ra, "手机号已被别人使用");

        // ---- 保存账号字段 ----
        user.setRealName(realName);
        user.setStudentNo(studentNo);
        user.setUsername(username);
        user.setPhone(phone);
        // T-016：所属组。空值 = 未分组（数据库允许 group_id 为 NULL）
        user.setGroupId(FormUtils.parseLong(groupId));
        userRepository.save(user);

        // ---- 保存档案字段（没有档案就顺手建一条）----
        StudentProfile profile = profileRepository.findById(id).orElseGet(() -> {
            StudentProfile created = new StudentProfile();
            created.setUserId(id);
            return created;
        });
        profile.setGrade(FormUtils.blankToNull(grade));
        profile.setMajorClass(FormUtils.blankToNull(majorClass));
        profile.setQq(FormUtils.blankToNull(qq));
        profile.setWechat(FormUtils.blankToNull(wechat));
        profile.setEmail(FormUtils.blankToNull(email));
        profile.setLanguages(FormUtils.blankToNull(languages));
        profile.setLevel(FormUtils.parseEnum(Level.class, level));
        profile.setJoinDate(joinDate);
        profileRepository.save(profile);

        ra.addFlashAttribute("saved", "已保存");
        return "redirect:/admin/students/" + id;
    }

    /** 渲染详情页（GET 和"出错回显"都用它） */
    private String renderDetail(Long id, Model model) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return "redirect:/admin/students";
        }
        model.addAttribute("user", user);
        model.addAttribute("profile", profileRepository.findById(id).orElse(null));
        model.addAttribute("allStatuses", UserStatus.values());
        // T-016：角色下拉只列【可以直接指派】的 4 种，不含 LEADER
        //   （组长身份从 study_groups.leader_user_id 派生，不存进 users.role，详见 Role.assignable() 注释）
        model.addAttribute("allRoles", Role.assignable());
        model.addAttribute("allLevels", Level.values());
        model.addAttribute("allGroups", groupRepository.findAllByOrderBySortOrderAsc());  // T-016 组下拉
        return "admin/student-detail";
    }

    /**
     * 组 id → 组名 的映射（T-016）。
     *
     * 【为什么需要】`User.groupId` 是**裸 Long**（当初的设计决定，见 CODE_MAP 6.2），
     *   所以列表页拿到的是个数字。要在页面上显示组名，就得把 id 翻成名字。
     *   这里一次性把所有组查出来做成 Map，**避免每一行都去查一次数据库**（N+1 问题）。
     */
    private Map<Long, String> groupNamesById() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (StudyGroup g : groupRepository.findAllByOrderBySortOrderAsc()) {
            map.put(g.getId(), g.getName());
        }
        return map;
    }

    /** 带一条错误信息回到详情页 */
    private String back(Long id, RedirectAttributes ra, String message) {
        ra.addFlashAttribute("error", message);
        return "redirect:/admin/students/" + id;
    }
}
