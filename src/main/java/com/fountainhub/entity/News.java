package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 新闻 / 公告（对应数据库的 news 表）—— T-023。
 *
 * ===========================================================================
 * 【它承载两类东西】
 *   · 公告（NOTICE）—— 「例行活动照常」「这周日常开放时间调整」
 *   · 活动报道（ACTIVITY）/ 比赛（CONTEST）/ 技术分享（TECH）
 *
 * 【三个必须记住的字段】
 *   status        DRAFT 草稿 / PUBLISHED 已发布
 *                 ⚠️ 需求：「**草稿状态的新闻游客访问不到**」→ T-026 必须按它过滤
 *   isTop         置顶（列表里排在最前面）
 *   isInternal    **仅登录可见**（比如"内部通知，不对外"）→ T-026 也要按它过滤
 *
 * 【⚠️ 正文是 LONGTEXT】
 *   一张带图的报道可能几千字，所以数据库用的是 LONGTEXT。
 *   ⚠️ **映射不能用 `@Lob`**（实测踩坑，详见下面 content 字段的注释）——
 *   要用 `columnDefinition = "LONGTEXT"`。用 validate 启动就能当场验证对不对
 *   （映射写错，应用**启动就会失败**，不会等到运行时才发现）。
 * ===========================================================================
 */
@Entity
@Table(name = "news")
@Getter
@Setter
@NoArgsConstructor
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private NewsCategory category;

    /** 封面图路径（可空）。真正上传图片是 T-025 */
    @Column(name = "cover_image", length = 255)
    private String coverImage;

    /**
     * 正文（富文本 / HTML）。数据库是 `LONGTEXT`。
     *
     * ⚠️ **为什么不用 `@Lob`**（2026-10-03 实测踩坑）：
     *   `@Lob String` 会让 Hibernate 以为这一列是 CLOB，
     *   于是启动时报
     *     Schema-validation: wrong column type ... found [longtext] but expecting [tinytext]
     *   **应用起不来**。MySQL 的长文本要直接用 `columnDefinition` 写明 LONGTEXT 才对。
     */
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NewsStatus status = NewsStatus.DRAFT;

    @Column(name = "is_top", nullable = false)
    private Boolean isTop = false;

    /** 仅登录可见（true 时游客看不到） */
    @Column(name = "is_internal", nullable = false)
    private Boolean isInternal = false;

    /** 发布人（users.id）。裸 Long，和全项目一致 */
    @Column(name = "author_id")
    private Long authorId;

    /** 发布时间。草稿为 null；点"发布"时才写上 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
