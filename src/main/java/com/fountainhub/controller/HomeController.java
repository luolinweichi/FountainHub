package com.fountainhub.controller;

import com.fountainhub.entity.News;
import com.fountainhub.entity.NewsStatus;
import com.fountainhub.entity.SiteSetting;
import com.fountainhub.repository.NewsRepository;
import com.fountainhub.repository.SiteSettingRepository;
import com.fountainhub.service.PublicAwardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 首页控制器（T-014 建骨架，T-027 接上最新新闻）。
 *
 * @Controller      表示这是一个「返回页面」的控制器
 * @GetMapping("/") 表示浏览器访问根路径时执行这个方法
 *
 * 方法返回的字符串 "index" 会去找
 *   src/main/resources/templates/index.html
 *
 * ===========================================================================
 * 🔒 【重要：首页是【公开页】】
 *   游客不登录就能看到它，所以取的新闻**必须**按游客视角过滤：
 *     ① 只取 status = PUBLISHED（**草稿绝不能出现在首页**）
 *     ② 跳过 isInternal（仅登录可见的，游客不该看到）
 *   这和 T-026 公开新闻列表用的是**同一条规则** ——
 *   公开页的红线只有一条，不能这里松、那里紧。
 * ===========================================================================
 */
@Controller
public class HomeController {

    /** 首页显示几条最新新闻 */
    private static final int LATEST_NEWS = 3;

    /** ⭐ 焦点图轮播最多几张（T-041；任务书写 3–5 张） */
    private static final int FOCUS_NEWS = 5;

    /** ⭐ 首页「获奖公示」最多几条（T-033；其余的进 /awards 看） */
    private static final int HOME_AWARDS = 4;

    private final NewsRepository newsRepository;
    private final SiteSettingRepository settingRepository;
    private final PublicAwardService publicAwardService;

    public HomeController(NewsRepository newsRepository,
                        SiteSettingRepository settingRepository,
                        PublicAwardService publicAwardService) {
        this.newsRepository = newsRepository;
        this.settingRepository = settingRepository;
        this.publicAwardService = publicAwardService;
    }

    /*
     * ⚠️【T-043 修正】这里原来写死了 systemName / clubName：
     *       model.addAttribute("clubName", "……");
     *   但 GlobalModelAdvice（@ControllerAdvice）已经统一提供这两个值，
     *   而 **Controller 比 @ControllerAdvice 后执行** → 这里的写死值会把正确的值【覆盖】掉，
     *   结果社团名一直显示旧的写死值，改成新名字也不生效 ❌
     *   所以：**全站唯一来源就是 GlobalModelAdvice，这里不要再设**。
     * 🔄 开源脱敏（2026-10-04）：注释里原本写着真实社名的历史值，已改成省略号占位
     *    （教训：注释里写过的真实名字，翻 GitHub 一样能看到，等于没脱敏）。
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("now", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 最新新闻（游客视角：已发布 + 非"仅登录可见"）
        List<News> latest = new ArrayList<>();
        for (News n : newsRepository.findByStatusOrderByIsTopDescPublishedAtDesc(NewsStatus.PUBLISHED)) {
            if (Boolean.TRUE.equals(n.getIsInternal())) {
                continue;   // 🔒 仅登录可见的不上首页
            }
            latest.add(n);
            if (latest.size() >= LATEST_NEWS) {
                break;
            }
        }
        model.addAttribute("latestNews", latest);

        // T-042：首页背景图（site_settings 的 home_background；没配就是 null = 用默认浅色底）
        //   ✅ 2026-10-04 已接线：index.html 的 `.fh-homewrap` 拿它铺满【整个首页】并叠白色 40% 遮罩
        //      （需求方口径：「是整个首页的背景图，不是 Hero 的」）
        model.addAttribute("homeBackground", settingRepository.findById(SiteSetting.KEY_HOME_BACKGROUND)
                .map(SiteSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(null));

        // ⭐ 焦点图轮播（T-041）：和最新新闻【同一条红线】—— 只取已发布 + 非仅登录可见。
        //   区别只在"取几条"和"排序"：轮播取 5 条（置顶优先），右栏取 3 条（最新）。
        List<News> focus = new ArrayList<>();
        for (News n : newsRepository.findByStatusOrderByIsTopDescPublishedAtDesc(NewsStatus.PUBLISHED)) {
            if (Boolean.TRUE.equals(n.getIsInternal())) {
                continue;   // 🔒 仅登录可见的不上首页轮播
            }
            focus.add(n);
            if (focus.size() >= FOCUS_NEWS) {
                break;
            }
        }
        model.addAttribute("focusNews", focus);

        // ⭐ T-033：首页「获奖公示」—— 与 /awards 展示墙**同一条规则**（都走 PublicAwardService）。
        //   【为什么不在这里自己写过滤】公开页的红线只有一条，两处各写一遍迟早会不一致
        //   （T-041 首页新闻就吃过这个亏）→ 所以规则只在 Service 里有一份。
        //   首页只取前 4 条，多出来的去 /awards 看（那个页面才有筛选）。
        List<Map<String, Object>> publicAwards = publicAwardService.publicRows();
        model.addAttribute("publicAwards",
                publicAwards.size() > HOME_AWARDS ? publicAwards.subList(0, HOME_AWARDS) : publicAwards);
        return "index";
    }

    // 🔄 R-004：原本放在这里的 /ping 已经搬到 HealthController。
    //    原因：/ping 是给监控用的探活接口，和「首页」没有关系。
    //    **URL 完全没有变化**，仍然是 /ping。
}
