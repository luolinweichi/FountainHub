package com.fountainhub.controller;

import com.fountainhub.entity.Award;
import com.fountainhub.entity.AwardLevel;
import com.fountainhub.service.PublicAwardService;
import com.fountainhub.util.FormUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 获奖展示墙（T-033 · 对外公开页）。
 *
 * ===========================================================================
 * 🔒 【这是【公开页】】游客不登录就能看（FR-8.7 明确要求「不登录即可浏览」）。
 *   所以：
 *     ① 它必须在 `SecurityConfig` 的 permitAll 白名单里（已加 `/awards`）；
 *     ② 展示什么由 {@link PublicAwardService} 说了算 —— **只显示本人同意公开的**；
 *     ③ 页面**不得出现**手机号 / 学号 / 班级 / 联系方式（FR-12.5 红线）；
 *     ④ 页面**不展示证书照片**（团队奖证书上有未同意公开的队友姓名）。
 *
 * ⚠️ 后台是 `/admin/awards`（T-032），本页是 `/awards` —— 两个不同的路径，别混。
 * ===========================================================================
 */
@Controller
public class PublicAwardController {

    private final PublicAwardService publicAwardService;

    public PublicAwardController(PublicAwardService publicAwardService) {
        this.publicAwardService = publicAwardService;
    }

    @GetMapping("/awards")
    public String wall(@RequestParam(required = false) String level,
                       @RequestParam(required = false) String year,
                       Model model) {
        // 非法筛选值一律当"没筛选"（不能给游客一个 500/400）
        AwardLevel lv = FormUtils.parseEnum(AwardLevel.class, level);
        Integer yr = parseIntOrNull(year);

        // ⭐ 唯一的公开口径来源（首页调的是同一个方法）
        List<Map<String, Object>> all = publicAwardService.publicRows();

        // 筛选下拉里的年份：只列**真的有公开获奖**的年份（避免选了个必然为空的年份）
        Set<Integer> years = new TreeSet<>(Collections.reverseOrder());
        for (Map<String, Object> r : all) {
            Object y = r.get("year");
            if (y instanceof Integer) {
                years.add((Integer) y);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : all) {
            Award a = (Award) r.get("award");
            if (lv != null && a.getAwardLevel() != lv) {
                continue;
            }
            if (yr != null && !yr.equals(r.get("year"))) {
                continue;
            }
            rows.add(r);
        }

        model.addAttribute("rows", rows);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("levels", AwardLevel.values());
        model.addAttribute("years", years);
        model.addAttribute("filterLevel", lv == null ? "" : lv.name());
        model.addAttribute("filterYear", yr == null ? "" : String.valueOf(yr));
        return "awards";
    }

    private Integer parseIntOrNull(String raw) {
        Long v = FormUtils.parseLong(raw);
        return v == null ? null : v.intValue();
    }
}
