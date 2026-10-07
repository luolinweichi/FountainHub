-- =====================================================================
-- FountainHub · 初始数据脚本
-- 版本: v1.0 (2026-10-02)
-- 前置: 先执行 01-schema.sql
-- =====================================================================
--
-- ⚠️ 关于用户账号（重要）
--
--   users.password_hash 是 NOT NULL，而 BCrypt 哈希无法手写。
--   因此本脚本【不插入任何用户】，改由 Spring Boot 程序启动时自动创建。
--
--   你需要写一个 DataInitializer（见 docs/06 项目骨架说明）：
--     - 若 users 表为空，则创建初始管理员
--       用户名 admin  /  密码 由环境变量 FH_INIT_PASSWORD 提供（源码里无明文）  /  角色 TEACHER
--     - 用 BCryptPasswordEncoder 生成哈希，绝不硬编码明文
--
--   测试学员账号也建议由后台页面手工创建，或由 DataInitializer 生成。
--
-- =====================================================================

USE fountainhub;

-- ---------- 学习小组 ----------
-- 🔄 开源脱敏 + 通用化（2026-10-04）：原来这里是「Python 组」「C 语言组」——
--   那是本社团的真实分组。现在改成一条中性的「示例组」，
--   使用者到后台「分组管理」页新建/改名成自己的分组即可（那一页现在支持新建 · 改名 · 删除）。
--   另外示例数据从 2 条减到 1 条：组名对每个社团都不一样，给示例不如给一条、然后让人改。
INSERT INTO study_groups (name, description, sort_order) VALUES
  ('示例组', '示例小组（请改成你自己的分组）', 1) AS new
-- ↑ 这里的 AS new 是「行别名」：MySQL 8.0.20 起把 VALUES() 这种写法标记为废弃，
--   改用别名引用新值，未来升级 MySQL 也不会挂（需要 MySQL 8.0.19+，我们的目标是 8.4 LTS）。
ON DUPLICATE KEY UPDATE description = new.description;

-- ---------- 示例新闻（用于前端联调） ----------
-- 🔄 开源脱敏（2026-10-04）：这三条是【唯一会随仓库开源出去的新闻数据】
--    （新装机的人跑本脚本就会看到），所以标题与正文里的
--    真实社团名与旧系统名已全部换成中性说法。
-- ⚠️ 故意【不写社团名】—— 免得使用者改了 CLUB_NAME 还要回来同步这里。
INSERT INTO news (title, category, content, status, is_top, is_internal, published_at)
VALUES
  ('社团管理系统正式启用', 'NOTICE',
   '<p>平台开始试运行，请各位成员登录后完善个人资料。</p>', 'PUBLISHED', 1, 0, NOW()),
  ('关于每周例会的通知', 'NOTICE',
   '<p>每周固定时间为全员例会时间，请准时到场并在系统中签到。</p>', 'PUBLISHED', 0, 0, NOW()),
  ('新成员招募宣讲会圆满结束', 'ACTIVITY',
   '<p>本次宣讲会共收到若干份报名，感谢大家的参与。</p>', 'PUBLISHED', 0, 0, NOW());

-- ---------- 示例章节（二期题库用，P1 只需表存在） ----------
INSERT INTO chapters (title, language, description, sort_order) VALUES
  ('C 语言基础：变量与数据类型', 'C', '示例章节，用于验证题库结构', 1),
  ('Python 基础：列表与字典', 'PYTHON', '示例章节，用于验证题库结构', 2);

-- ---------- 校验 ----------
SELECT
  (SELECT COUNT(*) FROM study_groups) AS 小组数,
  (SELECT COUNT(*) FROM news)         AS 新闻数,
  (SELECT COUNT(*) FROM chapters)     AS 章节数,
  (SELECT COUNT(*) FROM users)        AS 用户数_应为0;

SELECT '初始化完成：请在 Spring Boot 首次启动后创建用户' AS result;

