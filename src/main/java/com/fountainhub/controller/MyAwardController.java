package com.fountainhub.controller;

import com.fountainhub.entity.Award;
import com.fountainhub.entity.AwardImage;
import com.fountainhub.entity.AwardStudent;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AwardImageRepository;
import com.fountainhub.repository.AwardRepository;
import com.fountainhub.repository.AwardStudentRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.util.UserDisplay;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学员端「我的获奖」（T-032）。
 *
 * ===========================================================================
 * 🔒🔒 【这个页面存在的唯一理由：红线】
 *   红线原话：「获奖对外展示必须经**该学员本人同意**」（`award_students.is_public`）。
 *   所以：
 *     · 后台录入时**一律不同意**（管理员不能代勾，连勾选框都没有）；
 *     · 想公开，只能**学员自己**来这里打开开关；
 *     · 服务端**必须校验这一行是不是他自己的** —— 只靠页面显示"别人的 ID 点不到"是不够的，
 *       有人手工发一个 POST 就能改别人的意愿。校验不通过 → `AccessDeniedException` → 403。
 *
 * 【权限】登录即可（`/me/**` 本来就要求登录）。
 *   学员、组长、社长、老师都能用自己的账号看**自己的**获奖 ——
 *   因为查询是 `findByUserIdOrderByIdDesc(me.getId())`，天然只看得到自己。
 * ===========================================================================
 */
@Controller
@RequestMapping("/me/awards")
public class MyAwardController {

    private final AwardStudentRepository awardStudentRepository;
    private final AwardRepository awardRepository;
    private final AwardImageRepository awardImageRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public MyAwardController(AwardStudentRepository awardStudentRepository,
                             AwardRepository awardRepository,
                             AwardImageRepository awardImageRepository,
                             UserRepository userRepository,
                             CurrentUserService currentUserService) {
        this.awardStudentRepository = awardStudentRepository;
        this.awardRepository = awardRepository;
        this.awardImageRepository = awardImageRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        User me = currentUserService.require(authentication);

        // 只查"我"的关联行（不查全部再过滤 —— 从查询层就把别人排除掉）
        List<AwardStudent> myLinks = awardStudentRepository.findByUserIdOrderByIdDesc(me.getId());

        // 一次把需要的获奖与图片取出来（记录很少，避免 N+1）
        Map<Long, Award> awardById = new HashMap<>();
        for (Award a : awardRepository.findAll()) {
            awardById.put(a.getId(), a);
        }
        Map<Long, List<AwardImage>> imagesByAward = new HashMap<>();
        for (AwardImage img : awardImageRepository.findAll()) {
            imagesByAward.computeIfAbsent(img.getAwardId(), k -> new ArrayList<>()).add(img);
        }
        Map<Long, List<AwardStudent>> linksByAward = new HashMap<>();
        for (AwardStudent as : awardStudentRepository.findAll()) {
            linksByAward.computeIfAbsent(as.getAwardId(), k -> new ArrayList<>()).add(as);
        }
        Map<Long, User> userById = new HashMap<>();
        for (User u : userRepository.findAllByOrderByRealNameAsc()) {
            userById.put(u.getId(), u);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int publicCount = 0;
        for (AwardStudent link : myLinks) {
            Award a = awardById.get(link.getAwardId());
            if (a == null) {
                continue;   // 奖被删了（理论上 CASCADE 会一起删，这里只是兜底）
            }
            List<Map<String, Object>> teammates = new ArrayList<>();
            for (AwardStudent as : linksByAward.getOrDefault(a.getId(), Collections.emptyList())) {
                Map<String, Object> t = new LinkedHashMap<>();
                User u = userById.get(as.getUserId());
                t.put("name", UserDisplay.name(u));
                t.put("me", as.getUserId().equals(me.getId()));
                t.put("isPublic", Boolean.TRUE.equals(as.getIsPublic()));
                teammates.add(t);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("award", a);
            row.put("linkId", link.getId());
            row.put("isPublic", Boolean.TRUE.equals(link.getIsPublic()));
            row.put("teammates", teammates);
            row.put("images", imagesByAward.getOrDefault(a.getId(), Collections.emptyList()));
            rows.add(row);
            if (Boolean.TRUE.equals(link.getIsPublic())) {
                publicCount++;
            }
        }

        model.addAttribute("rows", rows);
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("publicCount", publicCount);
        return "me/awards";
    }

    /**
     * 打开 / 撤回「同意公开」。
     *
     * 【为什么用 POST + 显式 value，而不是"点一下就取反"】
     *   取反是"状态依赖"的操作：连点两次、或页面停留很久后提交，结果会出乎意料。
     *   显式传 `value=true/false` 更确定，也不会出现"重复提交把开关又改回去"。
     */
    @PostMapping("/{linkId}/public")
    public String setPublic(@PathVariable Long linkId,
                            @RequestParam(required = false) String value,
                            Authentication authentication,
                            RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        AwardStudent link = awardStudentRepository.findById(linkId).orElse(null);
        if (link == null) {
            ra.addFlashAttribute("error", "找不到这条获奖记录。");
            return "redirect:/me/awards";
        }

        // 🔒🔒 红线校验：只能改"自己那一行"。抛 403 而不是假装没发生。
        if (!link.getUserId().equals(me.getId())) {
            throw new AccessDeniedException("只能修改自己的获奖公开意愿");
        }

        boolean on = "true".equals(value);
        link.setIsPublic(on);
        awardStudentRepository.save(link);

        Award a = awardRepository.findById(link.getAwardId()).orElse(null);
        String name = a == null ? "这条获奖" : a.getCompetitionName();
        ra.addFlashAttribute("saved", on
                ? "已同意公开：" + name + "（会出现在对外展示墙）"
                : "已撤回公开：" + name + "（对外展示墙不再显示你）");
        return "redirect:/me/awards";
    }
}
