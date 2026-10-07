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
 * 上传文件登记（对应数据库的 upload_files 表）—— T-025。
 *
 * ===========================================================================
 * 【为什么要有这张表】图片躺在磁盘上，但我们要知道：
 *   · 谁传的（uploader_id）→ 出了问题能追溯
 *   · 原来叫什么（original_name）→ 给用户看的
 *   · 存到哪了（stored_path）→ 真正的磁盘路径
 *   · 多大、什么类型 → 排查和清理用
 *
 * ⚠️ 【最重要的一条】磁盘上的文件名是 **随机 UUID**，不是用户传的名字。
 *    original_name 只用来"给人看"，**绝不参与拼路径** ——
 *    否则用户传一个 ../../etc/passwd 就有可能写到目录外面去（路径穿越）。
 * ===========================================================================
 */
@Entity
@Table(name = "upload_files")
@Getter
@Setter
@NoArgsConstructor
public class UploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户上传时的原始文件名（只给人看，不用于落盘） */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** 服务器上的存储路径（相对上传目录，例如 2026/10/abc-uuid.png） */
    @Column(name = "stored_path", nullable = false, length = 255)
    private String storedPath;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    /** 谁传的（users.id） */
    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
