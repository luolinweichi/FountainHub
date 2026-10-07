package com.fountainhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站点级配置（映射第 20 张表 site_settings）。
 *
 * 【为什么是键值对表】
 *   站点级配置会慢慢长出来（首页背景图 → 以后可能有页脚文字、主题色）。
 *   用"键值对"表，加新配置只要插一行，**不用再改表结构**。
 *
 * 【⚠️ 为什么字段名叫 settingKey 而不是 key】
 *   key 和 value 在 MySQL 里是保留字，直接用会报语法错误，所以表里叫
 *   setting_key / setting_value，实体字段跟着叫 settingKey / settingValue。
 *
 * 【当前用到的键】
 *   · home_background —— 首页背景图的 URL（空 = 用默认背景）
 * ===========================================================================
 */
@Entity
@Table(name = "site_settings")
@Getter
@Setter
@NoArgsConstructor
public class SiteSetting {

    /** 首页背景图的配置键（T-042）。空值 / 不存在 = 使用默认背景 */
    public static final String KEY_HOME_BACKGROUND = "home_background";

    @Id
    @Column(name = "setting_key", length = 64)
    private String settingKey;

    @Column(name = "setting_value")
    private String settingValue;

    /** 数据库 ON UPDATE CURRENT_TIMESTAMP 自己维护，程序不许写 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
