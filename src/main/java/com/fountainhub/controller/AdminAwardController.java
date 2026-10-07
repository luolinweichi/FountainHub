package com.fountainhub.controller;

import com.fountainhub.entity.Award;
import com.fountainhub.entity.AwardImage;
import com.fountainhub.entity.AwardLevel;
import com.fountainhub.entity.AwardStudent;
import com.fountainhub.entity.Prize;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AwardImageRepository;
import com.fountainhub.repository.AwardRepository;
import com.fountainhub.repository.AwardStudentRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.util.FormUtils;
import com.fountainhub.util.UserDisplay;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 后台获奖管理（T-032 / M8）。
 *
 * ===========================================================================
 * 【权限】指导老师 / 正社长 / 副社长（权限矩阵「管理获奖」这三位）。
 *   🔒 **组长与学员一律 403** —— 项目负责人 2026-10-04 拍板：
 *      与 T-017「组长只能看本组姓名与人数、不能改任何数据、进不了后台」保持一致。
 *   🔒 **删除只有指导老师 + 正社长能做**（副社长不能删数据）→ 见 delete() 上的方法级注解。
 *
 * 【一条奖 = 三张表】
 *   awards（比赛信息） + award_students（谁得的，含**每个人的**公开意愿） + award_images（证书多图）
 *
 * 🔒🔒 【本控制器最重要的一条】`is_public` 绝不接受前端传入
 *   —— 新建时一律 false（不同意）。
 *   打开/撤回顾由**学员本人**在 `/me/awards` 操作（见 MyAwardController）。
 *   理由：红线「获奖对外展示必须经该学员本人同意」，
 *   管理员**不能**替学员表态，所以后台表单里根本没有那个勾选框（只显示只读状态）。
 *
 * 【成员为什么必须"差量同步"而不是清空重建】
 *   清空重建会把学员已经打开的公开意愿一起删掉（那张表上一行 = 一个人 + 他的意愿）。
 *   差量同步 = 保留勾选的（不动意愿）、新增勾选的（意愿默认不同意）、删除没勾的。
 *   证书图片没有意愿字段，所以那边用清空重建即可。
 * ===========================================================================
 */
@Controller
@RequestMapping("/admin/awards")
@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
public class AdminAwardController {

    /** 🔒 只接受站内上传出来的图片路径（T-025 的上传接口都放在 /uploads/ 下） */
    private static final String IMAGE_PREFIX = "/uploads/";

    /** 一条奖最多挂几张证书（避免有人一次传 200 张把页面压垮） */
    private static final int MAX_IMAGES = 12;

    private final AwardRepository awardRepository;
    private final AwardStudentRepository awardStudentRepository;
    private final AwardImageRepository awardImageRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AdminAwardController(AwardRepository awardRepository,
                                AwardStudentRepository awardStudentRepository,
                                AwardImageRepository awardImageRepository,
                                UserRepository userRepository,
                                CurrentUserService currentUserService) {
        this.awardRepository = awardRepository;
        this.awardStudentRepository = awardStudentRepository;
        this.awardImageRepository = awardImageRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    // ==================================================================
    // 列表（可按级别 / 年份筛选）
    // ==================================================================
    @GetMapping
    public String list(@RequestParam(required = false) String level,
                       @RequestParam(required = false) String year,
                       Model model) {
        // 非法筛选值一律当"没筛选"（不能给用户一个 500/400）
        AwardLevel lv = FormUtils.parseEnum(AwardLevel.class, level);
        Integer yr = parseIntOrNull(year);

        List<Award> all = awardRepository.findAllByOrderByAwardDateDescIdDesc();

        // 【为什么一次全查完再分组】获奖是几十条的量级；
        //   若每条奖都去查一次成员/图片，就是 N+1 次查询（列表页会一卡一卡的）。
        Map<Long, List<AwardStudent>> linksByAward = new HashMap<>();
        for (AwardStudent as : awardStudentRepository.findAll()) {
            linksByAward.computeIfAbsent(as.getAwardId(), k -> new ArrayList<>()).add(as);
        }
        Map<Long, Integer> imageCountByAward = new HashMap<>();
        for (AwardImage img : awardImageRepository.findAll()) {
            imageCountByAward.merge(img.getAwardId(), 1, Integer::sum);
        }
        Map<Long, User> userById = userById();

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Integer> years = new TreeSet<>(Collections.reverseOrder());
        for (Award a : all) {
            if (a.getAwardDate() != null) {
                years.add(a.getAwardDate().getYear());
            }
            if (lv != null && a.getAwardLevel() != lv) {
                continue;
            }
            if (yr != null && (a.getAwardDate() == null || a.getAwardDate().getYear() != yr)) {
                continue;
            }
            rows.add(row(a,
                    linksByAward.getOrDefault(a.getId(), Collections.emptyList()),
                    imageCountByAward.getOrDefault(a.getId(), 0),
                    userById));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("levels", AwardLevel.values());
        model.addAttribute("years", years);
        model.addAttribute("filterLevel", lv == null ? "" : lv.name());
        model.addAttribute("filterYear", yr == null ? "" : String.valueOf(yr));
        model.addAttribute("totalCount", all.size());
        return "admin/awards";
    }

    // ==================================================================
    // 新建
    // ==================================================================
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("award", new Award());
        model.addAttribute("isNew", true);
        model.addAttribute("levels", AwardLevel.values());
        model.addAttribute("prizes", Prize.values());
        model.addAttribute("candidates", userRepository.findAllByOrderByRealNameAsc());
        model.addAttribute("selectedIds", Collections.emptySet());
        model.addAttribute("consentByUser", Collections.emptyMap());
        // 表单里的证书图：paths 给 JS 用（初始化数组），imagePathsCsv 给隐藏字段用（JS 挂了也能保住已有图）
        model.addAttribute("paths", Collections.emptyList());
        model.addAttribute("imagePathsCsv", "");
        return "admin/award-form";
    }

    @PostMapping("/new")
    public String create(@RequestParam String competitionName,
                         @RequestParam(required = false) String organizer,
                         @RequestParam(required = false) String awardDate,
                         @RequestParam String awardLevel,
                         @RequestParam String prize,
                         @RequestParam(required = false) String advisorName,
                         @RequestParam(required = false) List<String> userIds,
                         @RequestParam(required = false) String imagePaths,
                         Authentication authentication,
                         RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        // ---------- 校验（非法值给人话提示，绝不 500）----------
        AwardLevel lv = FormUtils.parseEnum(AwardLevel.class, awardLevel);
        Prize pz = FormUtils.parseEnum(Prize.class, prize);
        if (FormUtils.blankToNull(competitionName) == null) {
            ra.addFlashAttribute("error", "比赛名称必须填。");
            return "redirect:/admin/awards/new";
        }
        if (lv == null || pz == null) {
            ra.addFlashAttribute("error", "级别和奖项必须从下拉里选。");
            return "redirect:/admin/awards/new";
        }
        LocalDate date = null;
        if (FormUtils.blankToNull(awardDate) != null) {
            date = FormUtils.parseDate(awardDate);
            if (date == null) {
                ra.addFlashAttribute("error", "获奖日期格式不对，请用日期选择器填（年-月-日）。");
                return "redirect:/admin/awards/new";
            }
        }

        // ---------- 保存获奖本体 ----------
        Award a = new Award();
        a.setCompetitionName(competitionName.trim());
        a.setOrganizer(FormUtils.blankToNull(organizer));
        a.setAwardDate(date);
        a.setAwardLevel(lv);
        a.setPrize(pz);
        a.setAdvisorName(FormUtils.blankToNull(advisorName));
        a.setCreatedBy(me.getId());          // 🔒 录入人从会话取，不接受前端传入
        awardRepository.save(a);

        // ---------- 团队成员 ----------
        int memberCount = replaceMembers(a.getId(), parseUserIds(userIds), false);

        // ---------- 证书图 ----------
        int imageCount = replaceImages(a.getId(), parseImagePaths(imagePaths));

        ra.addFlashAttribute("saved", "已录入获奖：" + a.getCompetitionName()
                + "（" + memberCount + " 位学员，" + imageCount + " 张证书）");
        return "redirect:/admin/awards";
    }

    // ==================================================================
    // 编辑
    // ==================================================================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Award a = awardRepository.findById(id).orElse(null);
        if (a == null) {
            ra.addFlashAttribute("error", "找不到这条获奖记录。");
            return "redirect:/admin/awards";
        }
        List<AwardStudent> links = awardStudentRepository.findByAwardId(id);
        Set<Long> selected = new LinkedHashSet<>();
        Map<Long, Boolean> consent = new HashMap<>();
        for (AwardStudent as : links) {
            selected.add(as.getUserId());
            consent.put(as.getUserId(), Boolean.TRUE.equals(as.getIsPublic()));
        }

        model.addAttribute("award", a);
        model.addAttribute("isNew", false);
        model.addAttribute("levels", AwardLevel.values());
        model.addAttribute("prizes", Prize.values());
        model.addAttribute("candidates", userRepository.findAllByOrderByRealNameAsc());
        model.addAttribute("selectedIds", selected);
        model.addAttribute("consentByUser", consent);
        // 已有证书图：按 sort_order 取出来 → 给 JS 初始化 + 给隐藏字段
        List<String> paths = new ArrayList<>();
        for (AwardImage img : awardImageRepository.findByAwardIdOrderBySortOrderAscIdAsc(id)) {
            paths.add(img.getImagePath());
        }
        model.addAttribute("paths", paths);
        model.addAttribute("imagePathsCsv", String.join(",", paths));
        return "admin/award-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @RequestParam String competitionName,
                         @RequestParam(required = false) String organizer,
                         @RequestParam(required = false) String awardDate,
                         @RequestParam String awardLevel,
                         @RequestParam String prize,
                         @RequestParam(required = false) String advisorName,
                         @RequestParam(required = false) List<String> userIds,
                         @RequestParam(required = false) String imagePaths,
                         RedirectAttributes ra) {
        Award a = awardRepository.findById(id).orElse(null);
        if (a == null) {
            ra.addFlashAttribute("error", "找不到这条获奖记录。");
            return "redirect:/admin/awards";
        }
        AwardLevel lv = FormUtils.parseEnum(AwardLevel.class, awardLevel);
        Prize pz = FormUtils.parseEnum(Prize.class, prize);
        if (FormUtils.blankToNull(competitionName) == null || lv == null || pz == null) {
            ra.addFlashAttribute("error", "比赛名称、级别、奖项都必须填。");
            return "redirect:/admin/awards/" + id + "/edit";
        }
        LocalDate date = null;
        if (FormUtils.blankToNull(awardDate) != null) {
            date = FormUtils.parseDate(awardDate);
            if (date == null) {
                ra.addFlashAttribute("error", "获奖日期格式不对，请用日期选择器填（年-月-日）。");
                return "redirect:/admin/awards/" + id + "/edit";
            }
        }

        // 🔒 只改这六项；created_by / created_at 一律不动
        a.setCompetitionName(competitionName.trim());
        a.setOrganizer(FormUtils.blankToNull(organizer));
        a.setAwardDate(date);
        a.setAwardLevel(lv);
        a.setPrize(pz);
        a.setAdvisorName(FormUtils.blankToNull(advisorName));
        awardRepository.save(a);

        // 成员差量同步（keepPresence = true：已勾选的**保留原行**，意愿不丢）
        int memberCount = replaceMembers(id, parseUserIds(userIds), true);
        int imageCount = replaceImages(id, parseImagePaths(imagePaths));

        ra.addFlashAttribute("saved", "已保存：" + a.getCompetitionName()
                + "（" + memberCount + " 位学员，" + imageCount + " 张证书）");
        return "redirect:/admin/awards";
    }

    // ==================================================================
    // 删除（🔒 只有指导老师 + 正社长；副社长没有这个权限 = 403）
    // ==================================================================
    /**
     * 【为什么用 POST 不用 GET】原因和新闻删除一样：GET 会被浏览器预取/前进后退
     *   意外触发，别人发你一个链接就能删数据。删除必须是个明确动作。
     * 【级联】award_students / award_images 的外键是 ON DELETE CASCADE，
     *   所以删掉这条奖，关联行和证书行会跟着消失（数据库负责，不用我们自己删）。
     *   ⚠️ 但**磁盘上的证书图片文件不会删** —— 与新闻配图的现状一致
     *      （宁可留几个孤儿文件，也不要误删别的记录还在用的图）。
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Award a = awardRepository.findById(id).orElse(null);
        if (a == null) {
            ra.addFlashAttribute("error", "找不到这条获奖记录。");
            return "redirect:/admin/awards";
        }
        awardRepository.delete(a);
        ra.addFlashAttribute("saved", "已删除获奖记录：" + a.getCompetitionName());
        return "redirect:/admin/awards";
    }

    // ==================================================================
    // 私有工具
    // ==================================================================

    /** 列表页一行要显示的东西 */
    private Map<String, Object> row(Award a, List<AwardStudent> links, int imageCount, Map<Long, User> userById) {
        List<Map<String, Object>> members = new ArrayList<>();
        int publicCount = 0;
        for (AwardStudent as : links) {
            Map<String, Object> m = new LinkedHashMap<>();
            User u = userById.get(as.getUserId());
            m.put("linkId", as.getId());
            m.put("name", UserDisplay.name(u));
            m.put("roleLabel", u == null ? "—" : u.getRole().getLabel());
            m.put("isPublic", Boolean.TRUE.equals(as.getIsPublic()));
            if (Boolean.TRUE.equals(as.getIsPublic())) {
                publicCount++;
            }
            members.add(m);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("award", a);
        row.put("members", members);
        row.put("publicCount", publicCount);
        row.put("memberCount", members.size());
        row.put("imageCount", imageCount);
        return row;
    }

    /**
     * 成员差量同步。
     *
     * @param keepExisting true = 已存在的关联**保留原行**（意愿不丢）；
     *                     false = 新建场景（此时本来就没有旧行，等价于直接插入）
     * @return 同步后的成员数
     */
    private int replaceMembers(Long awardId, Set<Long> submitted, boolean keepExisting) {
        List<AwardStudent> existing = awardStudentRepository.findByAwardId(awardId);
        Set<Long> existingIds = new LinkedHashSet<>();
        for (AwardStudent as : existing) {
            existingIds.add(as.getUserId());
            // 没勾选的 → 删掉（⚠️ 他的公开意愿会一起没；页面上已提示）
            if (!submitted.contains(as.getUserId())) {
                awardStudentRepository.delete(as);
            }
        }
        for (Long uid : submitted) {
            if (keepExisting && existingIds.contains(uid)) {
                continue;   // 已勾选且本来就在名单里 → 一行都不动（保住 is_public）
            }
            AwardStudent as = new AwardStudent();
            as.setAwardId(awardId);
            as.setUserId(uid);
            as.setIsPublic(false);   // 🔒 一律不同意，等学员本人自己打开
            awardStudentRepository.save(as);
        }
        return submitted.size();
    }

    /** 证书图清空重建（图片没有"意愿"这类状态，重建最省事、顺序可控） */
    private int replaceImages(Long awardId, List<String> paths) {
        List<AwardImage> old = awardImageRepository.findByAwardId(awardId);
        if (!old.isEmpty()) {
            awardImageRepository.deleteAll(old);
        }
        int order = 0;
        for (String p : paths) {
            AwardImage img = new AwardImage();
            img.setAwardId(awardId);
            img.setImagePath(p);
            img.setSortOrder(order++);
            awardImageRepository.save(img);
        }
        return paths.size();
    }

    /** 表单里的 user id 字符串 → 去重后的 Long 集合（非法值丢弃、不存在的人丢弃） */
    private Set<Long> parseUserIds(List<String> raw) {
        Set<Long> ids = new LinkedHashSet<>();
        if (raw == null) {
            return ids;
        }
        Set<Long> known = userById().keySet();
        for (String s : raw) {
            Long id = FormUtils.parseLong(s);
            if (id != null && known.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 🔒 图片路径清洗：只留站内 /uploads/ 开头的，去重、限张数 */
    private List<String> parseImagePaths(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String s : raw.split("[,;\\s]+")) {
            String p = s.trim();
            if (p.isEmpty() || !p.startsWith(IMAGE_PREFIX)) {
                continue;   // 🔒 非站内路径（比如 javascript: / 外站地址）直接丢
            }
            if (!out.contains(p)) {
                out.add(p);
            }
            if (out.size() >= MAX_IMAGES) {
                break;
            }
        }
        return out;
    }

    private Integer parseIntOrNull(String raw) {
        Long v = FormUtils.parseLong(raw);
        return v == null ? null : v.intValue();
    }

    private Map<Long, User> userById() {
        Map<Long, User> m = new HashMap<>();
        for (User u : userRepository.findAllByOrderByRealNameAsc()) {
            m.put(u.getId(), u);
        }
        return m;
    }
}
