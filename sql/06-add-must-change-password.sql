-- =====================================================================
-- FountainHub · 增量脚本 003：users 表新增 must_change_password（首次登录强制改密）
-- 数据库 : MySQL 8.0+
-- 日期   : 2026-10-07
-- 对应   : 「初始账号首次登录必须改密码」这条业务规则
-- 幂等   : ✅ 是。MySQL 8 的 `ADD COLUMN`【不支持】IF NOT EXISTS，
--          所以这里先查 information_schema，只有列不存在时才真正执行 ALTER。
--          重复执行不会报 "Duplicate column name"，可以放心多跑几次。
-- 执行   : mysql -u root -p fountainhub < 06-add-must-change-password.sql
-- 回滚   : ALTER TABLE users DROP COLUMN must_change_password;
--
-- ⚠️⚠️【必须"先跑这个脚本，再启动新 jar"】
--   application-local.yml 里是 `ddl-auto: validate` —— Hibernate【只校验、不改表】。
--   如果数据库缺这一列就启动新版本，应用会**直接启动失败**（报 schema validation 错误）。
-- =====================================================================

USE fountainhub;

-- 先看这一列在不在（0 = 不存在，需要加；1 = 已存在，跳过）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'users'
    AND COLUMN_NAME  = 'must_change_password'
);

-- 不存在才拼 ALTER 语句；已存在就只打印一句提示，什么都不改
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否必须先改密码：1=是（首次登录强制改密），改完由程序置 0'' AFTER status',
  'SELECT ''列 must_change_password 已存在，跳过'' AS info');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行完可以核对一下（应能看到 must_change_password 这一行）
-- SHOW COLUMNS FROM users LIKE 'must_change_password';
