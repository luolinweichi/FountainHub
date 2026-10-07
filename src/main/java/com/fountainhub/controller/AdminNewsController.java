package com.fountainhub.controller;

import com.fountainhub.entity.News;
import com.fountainhub.entity.NewsCategory;
import com.fountainhub.entity.NewsStatus;
import com.fountainhub.entity.User;
import com.fountainhub.entity.NewsImage;
import com.fountainhub.repository.NewsImageRepository;
import com.fountainhub.repository.NewsRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.util.FormUtils;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后台新闻管理（T-024 / M9）。
 *
 * ===========================================================================
 * 【权限】指导老师 / 正社长 / 副社长（权限矩阵里「发布通知/公告」就是这三位）。
 *   **组长 403** —— 和考勤管理一致。
 *
 * 【正文是 Markdown】
 *   news.content 存的是 **Markdown 原文**（不是渲染后的 HTML）。
 *   为什么存原文：存 HTML 的话，下次编辑面对一堆标签根本没法改。
 *   显示时由 static/js/md.js 渲染 —— 那个渲染器**先转义 HTML 再解析**，防 XSS ✅
 *   （同时它零依赖、不引 CDN，就算脚本没加载，页面上也是可读的 Markdown 原文）。
 *
 * 🔒 【防批量赋值】表单只接收四个字段：title / category / content / isInternal。
 *   isTop、status、authorId、publishedAt **一律由服务器决定**，不接受前端传入 ——
 *   否则有人改一下表单就能把自己设成"置顶+已发布"。
 * ===========================================================================
 */
@Controller
@RequestMapping("/admin/news")
@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
public class AdminNewsController {

    /** 匹配 Markdown 里的图片：![说明](/uploads/xxx.png) */
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\((/uploads/[^)\\s]+)\\)");

    private final NewsRepository newsRepository;
    private final NewsImageRepository newsImageRepository;
    private final CurrentUserService currentUserService;

    public AdminNewsController(NewsRepository newsRepository,
                               NewsImageRepository newsImageRepository,
                               CurrentUserService currentUserService) {
        this.newsRepository = newsRepository;
        this.newsImageRepository = newsImageRepository;
        this.currentUserService = currentUserService;
    }

    // ==================== 列表 ====================
    @GetMapping
    public String list(Model model) {
        List<News> all = newsRepository.findAllByOrderByIsTopDescCreatedAtDesc();
        model.addAttribute("newsList", all);
        model.addAttribute("categories", NewsCategory.values());
        return "admin/news";
    }

    // ==================== 新建 ====================
    @GetMapping("/new")
    public String newForm(Model model) {
        News n = new News();
        n.setCategory(NewsCategory.NOTICE);
        model.addAttribute("news", n);
        model.addAttribute("categories", NewsCategory.values());
        model.addAttribute("isNew", true);
        return "admin/news-form";
    }

    @PostMapping("/new")
    public String create(@RequestParam String title,
                         @RequestParam String category,
                         @RequestParam(required = false) String content,
                         @RequestParam(required = false) String coverImage,
                         @RequestParam(required = false) String isInternal,
                         Authentication authentication,
                         RedirectAttributes ra) {
        User me = currentUserService.require(authentication);
        NewsCategory cat = FormUtils.parseEnum(NewsCategory.class, category);
        if (FormUtils.blankToNull(title) == null || cat == null) {
            ra.addFlashAttribute("error", "标题和分类必须填。");
            return "redirect:/admin/news/new";
        }
        News n = new News();
        n.setTitle(title.trim());
        n.setCategory(cat);
        n.setContent(FormUtils.trimToEmpty(content));
        n.setCoverImage(FormUtils.blankToNull(coverImage));   // T-025 封面图
        n.setIsInternal("true".equals(isInternal));   // 只有勾了才是 true
        n.setIsTop(false);                            // 新建一律不置顶，要置顶去列表点按钮
        n.setStatus(NewsStatus.DRAFT);                // 新建一律是草稿 —— 🔒 不能由前端决定
        n.setAuthorId(me.getId());                    // 🔒 从会话取，不接受前端传入
        newsRepository.save(n);
        syncInlineImages(n);   // T-025：把正文里的插图登记到 news_images
        ra.addFlashAttribute("saved", "已新建草稿：" + n.getTitle());
        return "redirect:/admin/news";
    }

    // ==================== 编辑 ====================
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        News n = newsRepository.findById(id).orElse(null);
        if (n == null) {
            ra.addFlashAttribute("error", "找不到这条新闻。");
            return "redirect:/admin/news";
        }
        model.addAttribute("news", n);
        model.addAttribute("categories", NewsCategory.values());
        model.addAttribute("isNew", false);
        return "admin/news-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @RequestParam String title,
                         @RequestParam String category,
                         @RequestParam(required = false) String content,
                         @RequestParam(required = false) String coverImage,
                         @RequestParam(required = false) String isInternal,
                         RedirectAttributes ra) {
        News n = newsRepository.findById(id).orElse(null);
        if (n == null) {
            ra.addFlashAttribute("error", "找不到这条新闻。");
            return "redirect:/admin/news";
        }
        NewsCategory cat = FormUtils.parseEnum(NewsCategory.class, category);
        if (FormUtils.blankToNull(title) == null || cat == null) {
            ra.addFlashAttribute("error", "标题和分类必须填。");
            return "redirect:/admin/news/" + id + "/edit";
        }
        // 🔒 只改这四个字段；status / isTop / authorId / publishedAt 一律不动
        n.setTitle(title.trim());
        n.setCategory(cat);
        n.setContent(FormUtils.trimToEmpty(content));
        n.setCoverImage(FormUtils.blankToNull(coverImage));
        n.setIsInternal("true".equals(isInternal));
        newsRepository.save(n);
        syncInlineImages(n);   // T-025：正文改动后重新登记插图
        ra.addFlashAttribute("saved", "已保存：" + n.getTitle());
        return "redirect:/admin/news";
    }

    /**
     * 把正文 Markdown 里的插图登记进 news_images（T-025）。
     *
     * 【为什么这么做】正文里的 ![](/uploads/x.png) 本身就够显示了；
     *   单独登记一份，是为了将来能"按新闻查所有配图"（比如删除新闻时清理图片、
     *   或者 T-026 做图片画廊）。每次保存**先清空再重建**，保证和正文一致。
     */
    private void syncInlineImages(News n) {
        newsImageRepository.findAll().stream()
                .filter(img -> img.getNewsId().equals(n.getId()))
                .forEach(newsImageRepository::delete);
        Matcher m = MD_IMAGE.matcher(n.getContent() == null ? "" : n.getContent());
        List<String> paths = new ArrayList<>();
        while (m.find()) {
            if (!paths.contains(m.group(1))) {
                paths.add(m.group(1));   // 同一张图插两次，只登记一条
            }
        }
        int order = 0;
        for (String p : paths) {
            NewsImage img = new NewsImage();
            img.setNewsId(n.getId());
            img.setImagePath(p);
            img.setSortOrder(order++);
            newsImageRepository.save(img);
        }
    }

    // ==================== 发布 / 退回草稿 ====================
    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id, RedirectAttributes ra) {
        News n = newsRepository.findById(id).orElse(null);
        if (n == null) {
            ra.addFlashAttribute("error", "找不到这条新闻。");
            return "redirect:/admin/news";
        }
        if (n.getStatus() == NewsStatus.PUBLISHED) {
            // 退回草稿：顺便把发布时间清掉（它已经不是"发布中"了）
            n.setStatus(NewsStatus.DRAFT);
            n.setPublishedAt(null);
            ra.addFlashAttribute("saved", "已退回草稿：" + n.getTitle());
        } else {
            n.setStatus(NewsStatus.PUBLISHED);
            n.setPublishedAt(LocalDateTime.now());   // ⭐ 第一次发布时记下时间
            ra.addFlashAttribute("saved", "已发布：" + n.getTitle());
        }
        newsRepository.save(n);
        return "redirect:/admin/news";
    }

    // ==================== 置顶 ====================
    @PostMapping("/{id}/top")
    public String top(@PathVariable Long id, RedirectAttributes ra) {
        News n = newsRepository.findById(id).orElse(null);
        if (n == null) {
            ra.addFlashAttribute("error", "找不到这条新闻。");
            return "redirect:/admin/news";
        }
        n.setIsTop(!Boolean.TRUE.equals(n.getIsTop()));
        newsRepository.save(n);
        ra.addFlashAttribute("saved", (Boolean.TRUE.equals(n.getIsTop()) ? "已置顶：" : "已取消置顶：") + n.getTitle());
        return "redirect:/admin/news";
    }

    // ==================== 删除 ====================
    /**
     * 删除。
     * 【为什么用 POST 不用 GET】用 GET 的话，浏览器预取、后退、或者别人发你一个链接，
     *   都可能**莫名其妙把新闻删掉**。删除必须是个"明确的动作"。
     * （配图有外键 CASCADE，会跟着一起删 —— T-025 之后就有配图了）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        News n = newsRepository.findById(id).orElse(null);
        if (n == null) {
            ra.addFlashAttribute("error", "找不到这条新闻。");
            return "redirect:/admin/news";
        }
        newsRepository.delete(n);
        ra.addFlashAttribute("saved", "已删除：" + n.getTitle());
        return "redirect:/admin/news";
    }
}
