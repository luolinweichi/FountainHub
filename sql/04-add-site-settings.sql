-- =====================================================================
-- FountainHub · 增量脚本 002：新增 site_settings 表（站点级配置）
-- 数据库 : MySQL 8.0+
-- 日期   : 2026-10-04
-- 对应   : T-042 站点设置（管理员更换首页背景图）
-- 批准   : ✅ 项目负责人 2026-10-04 批准（A 方案：新增键值配置表）
-- 幂等   : ✅ 是。用 IF NOT EXISTS，可重复执行。
-- 回滚   : DROP TABLE IF EXISTS site_settings;
-- =====================================================================

USE fountainhub;

-- ---------------------------------------------------------------------
-- site_settings  站点级配置（键值对）
--
-- 【为什么用键值表，而不是给每项配置开一个字段】
--   站点级配置会慢慢长出来（背景图 → 以后可能有页脚文字、主题色）。
--   开一个'键值对'表，加新配置只需要插一行，**不用再改表结构**。
--
-- 【⚠️ 字段名为什么带 setting_ 前缀】
--   key 和 value 在 MySQL 里是【保留字】，直接用会报语法错误。
--   所以写成 setting_key / setting_value。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS site_settings (
  setting_key   VARCHAR(64) NOT NULL COMMENT '配置项名称，如 home_background' ,
  setting_value TEXT        NULL     COMMENT '配置值，如 /uploads/2026/10/xxx.png' ,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点级配置（键值对）';
