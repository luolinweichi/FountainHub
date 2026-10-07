package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 新闻配图（对应数据库的 news_images 表）—— T-023。
 *
 * 【为什么单独一张表，不在 news 里存一串路径】
 *   一张新闻可能有 N 张图，而且要能**排序**。用逗号拼一个字符串的话，
 *   将来想删掉中间一张、或者调整顺序，都得解析字符串 —— 很别扭。
 *   单独一张表，加一张就是插一行，排排序就是改 sort_order ✅
 *
 * ⚠️ news_id 上有外键 ON DELETE CASCADE：删新闻时配图自动跟着删 ✅
 */
@Entity
@Table(name = "news_images")
@Getter
@Setter
@NoArgsConstructor
public class NewsImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 属于哪条新闻（news.id）。裸 Long */
    @Column(name = "news_id", nullable = false)
    private Long newsId;

    /** 图片路径（相对 upload 目录） */
    @Column(name = "image_path", nullable = false, length = 255)
    private String imagePath;

    /** 排序（小的在前） */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
