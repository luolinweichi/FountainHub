package com.fountainhub.repository;

import com.fountainhub.entity.News;
import com.fountainhub.entity.NewsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 新闻仓库（T-023）。
 * ⚠️ 派生查询用 Java 属性名（isTop / publishedAt / createdAt），不是数据库列名。
 */
public interface NewsRepository extends JpaRepository<News, Long> {

    /**
     * 公开列表用（T-026）：已发布的，**置顶在前**，然后按发布时间倒序。
     *
     * ⚠️ 注意：这个方法**只过滤了 status**。调用方还要再排掉 `isInternal`（仅登录可见）的，
     *    以及根据"游客还是登录用户"决定要不要显示内部通知 ——
     *    这类"看人下菜"的逻辑放在 Controller 里更清楚。
     */
    List<News> findByStatusOrderByIsTopDescPublishedAtDesc(NewsStatus status);

    /**
     * 后台列表用（T-024）：**全部**（含草稿），置顶在前，按创建时间倒序。
     */
    List<News> findAllByOrderByIsTopDescCreatedAtDesc();

    /**
     * 公开列表按分类查（T-026）：已发布 + 指定分类，置顶在前。
     */
    List<News> findByStatusAndCategoryOrderByIsTopDescPublishedAtDesc(NewsStatus status, com.fountainhub.entity.NewsCategory category);
}
