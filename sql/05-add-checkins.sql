-- =====================================================================
-- FountainHub · 增量脚本 05：补回 checkins 表
-- 日期: 2026-10-04
-- 适用: **已经建过库**的环境（全新安装不需要跑 —— 新版 01-schema.sql 里已经建了这张表）
-- =====================================================================
--
-- 【为什么会有这个脚本（一段反复，如实记录）】
--   2026-10-04 当天发生过两次相反的决定：
--     ① 先决定"撤掉整个学习功能" → 于是我把 `checkins` 表从 `01-schema.sql` 里删了，
--        并且写了 `sql/05-drop-checkins.sql` 让已建库的人把表删掉；
--     ② 随后项目负责人改判：**表加回来**。
--        理由是：**二期的题库 API 契约（`docs/10`）整份建立在
--        "答对一题 → 往 `checkins` 写一行"之上** —— 表没了，那份契约就悬空了。
--
--   最终结论（三件事分开看，别混）：
--     · **表**：保留 ✅（本脚本负责给"已经删过"的库补回来；幂等，跑几次都一样）
--     · **界面 / 学习相关功能**：仍然不做 ❌（热力图、做题排行榜、`/me/study` 都已下线）
--     · **Java 侧**：保持删除状态（`Checkin` / `CheckinMetric` / `CheckinSource` /
--       `CheckinRepository` 都在 git 历史里，二期要用时捞回来即可）
--
-- 【执行】
--   & "C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p fountainhub < sql/05-add-checkins.sql
--
-- ⚠️ 你**没跑过** `05-drop-checkins.sql` 的话，跑本脚本也不会出错（`IF NOT EXISTS`）。
-- =====================================================================

USE fountainhub;

CREATE TABLE IF NOT EXISTS checkins (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL COMMENT '学员',
  checkin_date  DATE            NOT NULL COMMENT '打卡日期',
  metric_type   VARCHAR(30)     NOT NULL COMMENT 'PROBLEM_COUNT 题数 / STUDY_MINUTES 时长 / CHAPTER_COUNT 章节数',
  metric_value  INT             NOT NULL DEFAULT 0 COMMENT '数值',
  source        VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 自助 / QUIZ 题库 / OJ 外链 / ADMIN 管理员录入',
  note          VARCHAR(500)    NULL COMMENT '备注',
  operator_id   BIGINT UNSIGNED NULL COMMENT '若非本人填写，记录操作人',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkins (user_id, checkin_date, metric_type),
  KEY idx_checkins_date (checkin_date),
  CONSTRAINT fk_checkins_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习打卡记录（二期题库用；界面暂不做）';

-- ---------- 校验 ----------
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = 'fountainhub' AND table_name = 'checkins')           AS checkins应已存在_应为1,
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = 'fountainhub')                                       AS 库中表总数_应为20;

SELECT 'checkins 表已就位（二期题库可直接使用）' AS result;
