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
 * 获奖证书照片（对应数据库的 `award_images` 表）—— T-032（M8）。
 *
 * 【为什么单独一张表】一条奖可能有**多张**证书照（团队奖每人一张、或者正反面），
 *   所以不能像新闻封面那样在 awards 上放一个 cover_image 列。
 *
 * 【图片文件本身在哪】走已有的 `POST /admin/upload`（T-025）——
 *   白名单校验 + 随机 UUID 文件名 + 5MB 上限，存到 `uploads/年/月/` 下，
 *   这里只存**相对路径**（如 `/uploads/2026/10/xxx.png`）。
 *
 * 🔒 【路径必须校验】写入前要求每个 `imagePath` 都以 `/uploads/` 开头 ——
 *   否则有人能在表单里塞 `javascript:...` 或外站图片地址，
 *   页面渲染成 `<img src="...">` 就成了注入点。
 * ===========================================================================
 */
@Entity
@Table(name = "award_images")
@Getter
@Setter
@NoArgsConstructor
public class AwardImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 属于哪条获奖（awards.id）。裸 Long */
    @Column(name = "award_id", nullable = false)
    private Long awardId;

    /** 图片路径（必须 /uploads/ 开头） */
    @Column(name = "image_path", nullable = false, length = 255)
    private String imagePath;

    /** 排序（按上传/保存顺序递增） */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
