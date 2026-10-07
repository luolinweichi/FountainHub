package com.fountainhub.controller;

import com.fountainhub.entity.SiteTextKeys;
import com.fountainhub.service.SiteSettingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 社团介绍 / 招新页（T-028）。
 *
 * ===========================================================================
 * 🔒 【这是公开页】
 *   游客就能打开，所以：
 *     · 页面上【绝对不能】出现任何学员姓名 / 手机号 / 联系方式（T-029 红线）
 *     · 页面里的人名一律是【虚构的占位成员】，不能来自数据库
 *
 * 【为什么这里一个模型都不加】
 *   systemName / clubName 由 GlobalModelAdvice 统一提供 ✅
 *   本页内容全是静态文案（示例内容，使用者请自行替换），不需要查库。
 *
 * 【导航入口】顺序由需求方 2026-10-04 指定：首页 → 社团介绍 → 新闻 → 学习 → 管理 → 我的
 *   /about 已经在 SecurityConfig 的 permitAll 白名单里，本任务不用改安全配置。
 * ===========================================================================
 */
@Controller
public class AboutController {

    private final SiteSettingService siteSettingService;

    public AboutController(SiteSettingService siteSettingService) {
        this.siteSettingService = siteSettingService;
    }

    @GetMapping("/about")
    public String page(Model model) {
        // 🔄 2026-10-07：其余文案都由 GlobalModelAdvice 注入的 ${siteText} 提供，
        //   只有「价值观标签墙」需要的是**一个列表**（一行一个标签），
        //   所以在**只有这一页**要用的地方单独取一次 —— 不为了它给全站都加一次查询 ✅
        model.addAttribute("siteTags", siteSettingService.lines(SiteTextKeys.KEY_VALUES_TAGS));
        model.addAttribute("heroTags", siteSettingService.lines(SiteTextKeys.KEY_HERO_TAGS));
        return "about";
    }
}
