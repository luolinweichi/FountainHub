package com.fountainhub.controller;

import com.fountainhub.entity.SiteSetting;
import com.fountainhub.entity.SiteTextKeys;
import com.fountainhub.repository.SiteSettingRepository;
import com.fountainhub.service.SiteSettingService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台「站点设置」（T-042）—— 目前只有一项：更换【首页背景图】。
 *
 * ===========================================================================
 * 【需求口径（项目负责人 2026-10-04 定）】
 *   ① **一张固定**背景图，不做多张轮换
 *   ② **只作用首页**，其他页面保持纯色底
 *   ③ 可上传替换、可恢复默认
 *   ④ 首页要有**半透明遮罩**保证文字可读
 *
 * 【图片怎么上传的】
 *   复用现有的 POST /admin/upload（白名单 + 随机 UUID 文件名 + 5MB 限制）。
 *   本页只负责「把上传得到的 URL 记下来」—— 不自己写一套上传逻辑 ✅
 *
 * 【存哪】site_settings 表的 home_background 键（T-042 批准的新表）
 * ===========================================================================
 */
@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    private final SiteSettingRepository settingRepository;
    /** 2026-10-07：全站文案的读写走它（唯一入口） */
    private final SiteSettingService siteSettingService;

    public AdminSettingsController(SiteSettingRepository settingRepository,
                                   SiteSettingService siteSettingService) {
        this.settingRepository = settingRepository;
        this.siteSettingService = siteSettingService;
    }

    /** 读取当前首页背景图 URL（没有就返回 null = 用默认背景） */
    public static String readBackground(SiteSettingRepository repo) {
        return repo.findById(SiteSetting.KEY_HOME_BACKGROUND)
                .map(SiteSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String page(Model model) {
        model.addAttribute("background", readBackground(settingRepository));

        // 🔄 2026-10-07 全站文案配置：
        //   · textGroups —— 分组结构（面板 → 折叠子项 → 字段项）。
        //       后台模板**循环生成**表单，不用手写 40 多个输入框（手写必漏、必拼错 name ✅）
        //   · textValues —— 配置的**原值**（没填就是空）。
        //       后台必须如实显示"你到底填了没有"，**不能拿占位符骗管理员**；
        //       前台那套"空值显示占位符"的兜底在 SiteSettingService.allTexts() 里 ✅
        model.addAttribute("textGroups", SiteTextKeys.grouped());
        model.addAttribute("textValues", siteSettingService.rawValues());
        return "admin/settings";
    }

    /**
     * 保存全部文案（2026-10-07）。
     *
     * 【为什么用 Map 接参数，而不是写 40 多个 @RequestParam】
     *   表单里每个输入框的 `name` **就是配置键本身**（如 `home.hero.title`），
     *   所以把整个表单一次收成 Map，再交给 Service 按注册表过滤 + 保存最省事，
     *   而且**以后加文案不用改这个方法** ✅
     *
     * 🔒 安全：只接受 SiteTextKeys 里**登记过**的键（白名单在 Service 里守着）——
     *   否则能进这个接口的人就能往 site_settings 塞任意键（比如覆盖 home_background）。
     */
    @PostMapping("/text")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String saveTexts(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        siteSettingService.saveTexts(params);
        ra.addFlashAttribute("saved", "全站文案已保存。");
        return "redirect:/admin/settings";
    }

    /** 保存新的首页背景图（URL 来自 POST /admin/upload 的返回值） */
    @PostMapping("/background")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String saveBackground(@RequestParam(required = false) String url, RedirectAttributes ra) {
        if (url == null || url.isBlank()) {
            ra.addFlashAttribute("error", "没有收到图片地址，请重新上传。");
            return "redirect:/admin/settings";
        }
        SiteSetting s = settingRepository.findById(SiteSetting.KEY_HOME_BACKGROUND).orElseGet(() -> {
            SiteSetting n = new SiteSetting();
            n.setSettingKey(SiteSetting.KEY_HOME_BACKGROUND);
            return n;
        });
        s.setSettingValue(url);
        settingRepository.save(s);
        ra.addFlashAttribute("saved", "首页背景图已更新。");
        return "redirect:/admin/settings";
    }

    /** 恢复默认背景：把配置值清空（不是删图，图还在 uploads 里） */
    @PostMapping("/background/reset")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String resetBackground(RedirectAttributes ra) {
        settingRepository.findById(SiteSetting.KEY_HOME_BACKGROUND).ifPresent(s -> {
            s.setSettingValue("");
            settingRepository.save(s);
        });
        ra.addFlashAttribute("saved", "已恢复为默认背景。");
        return "redirect:/admin/settings";
    }
}
