package com.fountainhub.controller;

import com.fountainhub.entity.News;
import com.fountainhub.entity.NewsCategory;
import com.fountainhub.entity.NewsStatus;
import com.fountainhub.repository.NewsRepository;
import com.fountainhub.util.FormUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * 对外公开的新闻页（T-026）—— 游客不用登录就能看。
 *
 * ===========================================================================
 * 🔒 【本任务唯一、也是最重要的红线】
 *   spec 原文：「**草稿状态的新闻游客访问不到**」，而且特别注明
 *   「**游客直接访问详情 URL 也看不到**」。
 *
 *   所以两道过滤，**一处都不能少**：
 *     ① 列表：只查 status = PUBLISHED
 *     ② 详情：**也必须是 PUBLISHED** —— 草稿即使被人猜到了 id 也一律 404
 *        （用 404 而不是 403：**不告诉他"这里有东西但你不许看"**，连"存在"都不暴露）
 *
 *   isInternal（仅登录可见）同理：游客看不到、也访问不到详情。
 *
 * 【为什么分页不用 Spring Data 的 Pageable】
 *   新闻量很小（一期几十条），而且我们要在过滤掉 isInternal 之后再分页 ——
 *   用 Pageable 反而要在数据库层做两遍过滤。**在内存里筛完再切页**最直白、最不容易错。
 * ===========================================================================
 */
@Controller
public class PublicNewsController {

    /** 每页几条 */
    private static final int PAGE_SIZE = 6;

    private final NewsRepository newsRepository;

    public PublicNewsController(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    // ==================== 列表 ====================
    @GetMapping("/news")
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false, defaultValue = "0") Integer page,
                       Authentication authentication,
                       Model model) {
        boolean loggedIn = isLoggedIn(authentication);
        NewsCategory cat = FormUtils.parseEnum(NewsCategory.class, category);

        // ① 只拿"已发布"的（草稿一律进不来）
        List<News> published = (cat == null)
                ? newsRepository.findByStatusOrderByIsTopDescPublishedAtDesc(NewsStatus.PUBLISHED)
                : newsRepository.findByStatusAndCategoryOrderByIsTopDescPublishedAtDesc(NewsStatus.PUBLISHED, cat);

        // ② 再排掉"仅登录可见"的（游客看不到）
        List<News> visible = new ArrayList<>();
        for (News n : published) {
            if (!Boolean.TRUE.equals(n.getIsInternal()) || loggedIn) {
                visible.add(n);
            }
        }

        // ③ 内存里切页
        int total = visible.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        int p = (page == null || page < 0) ? 0 : page;
        if (p >= totalPages) {
            p = totalPages - 1;
        }
        int from = p * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        List<News> pageItems = visible.subList(from, to);

        model.addAttribute("newsList", pageItems);
        model.addAttribute("total", total);
        model.addAttribute("page", p);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("categories", NewsCategory.values());
        model.addAttribute("currentCategory", cat == null ? "" : cat.name());
        model.addAttribute("loggedIn", loggedIn);
        return "news/list";
    }

    // ==================== 详情 ====================
    @GetMapping("/news/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        News n = newsRepository.findById(id).orElse(null);
        boolean loggedIn = isLoggedIn(authentication);

        // 🔒 草稿 → 404（连"存在"都不暴露）
        if (n == null || n.getStatus() != NewsStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // 🔒 仅登录可见 → 游客 404
        if (Boolean.TRUE.equals(n.getIsInternal()) && !loggedIn) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("news", n);
        model.addAttribute("loggedIn", loggedIn);
        return "news/detail";
    }

    /** 判断"是不是登录用户"（游客拿到的是 AnonymousAuthenticationToken） */
    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
