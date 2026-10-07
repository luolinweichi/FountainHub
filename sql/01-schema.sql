-- =====================================================================
-- FountainHub 社团管理系统 · 数据库建表脚本
-- 数据库 : MySQL 8.0+
-- 字符集 : utf8mb4 / utf8mb4_unicode_ci
-- 版本   : v1.1  (2026-10-04)
-- 说明   : 一期(P1)完整表结构，含二期题库预留表(空表)
--          🔄 v1.1 新增第 19 张表 password_reset_requests（忘记密码申请，T-012B）
-- 执行   : mysql -u root -p < 01-schema.sql
-- =====================================================================

-- 注意：下面这行会删除整个库，仅在你的开发机上执行时手动取消注释
-- DROP DATABASE IF EXISTS fountainhub;
--
-- ⚠️ 本脚本【不是幂等的】：CREATE TABLE 用了 IF NOT EXISTS 可以重复执行，
--    但后面那句 ALTER TABLE ... ADD CONSTRAINT 在第二次执行时会报「外键已存在」而中断。
--    所以要重新来一遍，请先取消上面 DROP DATABASE 那一行的注释，再执行本脚本。

CREATE DATABASE IF NOT EXISTS fountainhub
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE fountainhub;

-- 关闭外键检查便于反复执行本脚本
SET FOREIGN_KEY_CHECKS = 0;


-- ---------------------------------------------------------------------
-- 1. study_groups  学习小组（Python 组 / C 语言组 / 竞赛组）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_groups (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name           VARCHAR(50)     NOT NULL                COMMENT '组名，如 Python 组',
  description    VARCHAR(255)    NULL                    COMMENT '说明',
  leader_user_id BIGINT UNSIGNED NULL                    COMMENT '组长用户ID（外键在 users 之后补）',
  sort_order     INT             NOT NULL DEFAULT 0      COMMENT '显示排序',
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_study_groups_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习小组';


-- ---------------------------------------------------------------------
-- 2. users  用户（所有角色共用一张表，靠 role 区分）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username       VARCHAR(50)     NOT NULL COMMENT '登录用户名（唯一）',
  student_no     VARCHAR(30)     NULL     COMMENT '学号（唯一，管理员可空）',
  phone          VARCHAR(20)     NULL     COMMENT '手机号（唯一，仅联系用，严禁外显）',
  password_hash  VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希，绝不存明文',
  real_name      VARCHAR(50)     NOT NULL COMMENT '真实姓名',
  role           VARCHAR(30)     NOT NULL COMMENT 'TEACHER/PRESIDENT/VICE_PRESIDENT/LEADER/STUDENT',
  logistics_duty TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否兼管后勤（职责标记，不改变角色）',
  group_id       BIGINT UNSIGNED NULL     COMMENT '所属组',
  status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'PENDING/ACTIVE/LEFT/GRADUATED/DISABLED',
  last_login_at  DATETIME        NULL     COMMENT '最后登录时间',
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username   (username),
  UNIQUE KEY uk_users_student_no (student_no),
  UNIQUE KEY uk_users_phone      (phone),
  KEY idx_users_role  (role),
  KEY idx_users_group (group_id),
  KEY idx_users_name  (real_name),
  CONSTRAINT fk_users_group FOREIGN KEY (group_id) REFERENCES study_groups (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（全部角色）';

-- 补上 study_groups -> users 的外键（循环依赖，只能建表后补）
ALTER TABLE study_groups
  ADD CONSTRAINT fk_groups_leader FOREIGN KEY (leader_user_id) REFERENCES users (id) ON DELETE SET NULL;


-- ---------------------------------------------------------------------
-- 3. student_profiles  学员档案（与 users 一对一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_profiles (
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID（同时为主键）',
  grade       VARCHAR(20)     NULL COMMENT '年级，如 2025级',
  major_class VARCHAR(50)     NULL COMMENT '专业/班级',
  qq          VARCHAR(20)     NULL COMMENT 'QQ',
  wechat      VARCHAR(50)     NULL COMMENT '微信',
  email       VARCHAR(100)    NULL COMMENT '邮箱',
  join_date   DATE            NULL COMMENT '入社日期',
  languages   VARCHAR(100)    NULL COMMENT '擅长语言，逗号分隔',
  level       VARCHAR(20)     NULL COMMENT '阶段 BEGINNER/INTERMEDIATE/CONTEST',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员档案';


-- ---------------------------------------------------------------------
-- 4. comments  学员评语（管理员对学员的长期记录，可多条）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comments (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id BIGINT UNSIGNED NOT NULL COMMENT '被评价的学员',
  author_id  BIGINT UNSIGNED NOT NULL COMMENT '写评语的人',
  content    TEXT            NOT NULL COMMENT '评语内容',
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_comments_student (student_id, created_at),
  CONSTRAINT fk_comments_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_comments_author  FOREIGN KEY (author_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员评语';


-- ---------------------------------------------------------------------
-- 5. chapters  章节（二期题库的组织单位，P1 先建空表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chapters (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title       VARCHAR(100)    NOT NULL COMMENT '章节标题',
  language    VARCHAR(20)     NULL     COMMENT '方向 PYTHON/C',
  description VARCHAR(500)    NULL     COMMENT '章节说明',
  sort_order  INT             NOT NULL DEFAULT 0 COMMENT '排序',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chapters_lang (language, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节（二期题库）';


-- ---------------------------------------------------------------------
-- 6. questions  选择题（二期，P1 建空表并预留接口）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS questions (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  chapter_id BIGINT UNSIGNED NOT NULL COMMENT '所属章节',
  type       VARCHAR(20)     NOT NULL COMMENT 'SINGLE 单选 / MULTIPLE 多选 / JUDGE 判断',
  stem       TEXT            NOT NULL COMMENT '题干',
  options    JSON            NULL     COMMENT '选项，如 ["A. xx","B. yy"]',
  answer     JSON            NOT NULL COMMENT '正确答案，如 ["A"] 或 ["A","C"]',
  analysis   TEXT            NULL     COMMENT '答案解析',
  image_path VARCHAR(255)    NULL     COMMENT '题干配图',
  code_block TEXT            NULL     COMMENT '代码片段',
  sort_order INT             NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_questions_chapter (chapter_id, sort_order),
  CONSTRAINT fk_questions_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选择题（二期题库）';


-- ---------------------------------------------------------------------
-- 7. quiz_answers  答题记录（二期）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_answers (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  question_id    BIGINT UNSIGNED NOT NULL,
  submitted      JSON            NOT NULL COMMENT '学员提交的答案',
  is_correct     TINYINT(1)      NOT NULL COMMENT '是否正确',
  answered_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_answers_user     (user_id, answered_at),
  KEY idx_answers_question (question_id),
  CONSTRAINT fk_answers_user     FOREIGN KEY (user_id)     REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录（二期题库）';


-- ---------------------------------------------------------------------
-- 8. oj_problems  外部 OJ 编程题（跳转链接，二期）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oj_problems (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title      VARCHAR(200)    NOT NULL COMMENT '题目标题',
  chapter_id BIGINT UNSIGNED NULL     COMMENT '所属章节（可空）',
  difficulty VARCHAR(20)     NULL     COMMENT 'EASY/MEDIUM/HARD',
  platform   VARCHAR(20)     NOT NULL COMMENT 'LUOGU 洛谷 / LEETCODE 力扣 / NOWCODER 牛客 / OTHER',
  url        VARCHAR(500)    NOT NULL COMMENT '外部题目链接',
  sort_order INT             NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oj_chapter (chapter_id),
  CONSTRAINT fk_oj_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部OJ编程题（二期）';


-- ---------------------------------------------------------------------
-- 9. oj_completions  外链题完成标记（二期）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oj_completions (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  problem_id   BIGINT UNSIGNED NOT NULL,
  status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待确认 / CONFIRMED 已确认 / REJECTED 已驳回',
  confirmed_by BIGINT UNSIGNED NULL COMMENT '确认人',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_oj_completion (user_id, problem_id),
  CONSTRAINT fk_ojc_user    FOREIGN KEY (user_id)    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_ojc_problem FOREIGN KEY (problem_id) REFERENCES oj_problems (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外链题完成标记（二期）';


-- ---------------------------------------------------------------------
-- 10. checkins  学习打卡记录  ★核心表（二期题库要用）
--     设计要点：一天可有多条不同指标类型的记录；
--     二期题库上线后 source=QUIZ 自动写入，热力图与表结构都不用改。
--
-- 🔄 2026-10-04 的一段反复（记在这里，避免以后又踩）：
--    当天先按"撤掉学习功能"把本表删了，随后项目负责人决定**把表加回来** ——
--    理由：**二期的题库 API 契约（docs/10）整份建立在"答对一题往 checkins 写一行"之上**，
--    表没了契约就悬空了。所以结论是：
--      · **表保留**（本表；二期直接可用，`sql/05-add-checkins.sql` 给已经删过的库补回来）
--      · **界面与"学习"相关功能仍然不做**（热力图、做题排行榜、/me/study 都已下线）
--      · Java 侧也保持删除状态（Checkin / CheckinMetric / CheckinSource / CheckinRepository
--        都在 git 历史里，二期要用时捞回来即可）
--    类比：**先把仓库盖好，货（二期）以后再进**。
-- ---------------------------------------------------------------------
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


-- ---------------------------------------------------------------------
-- 11. awards  获奖记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS awards (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  competition_name VARCHAR(200)    NOT NULL COMMENT '比赛名称',
  organizer        VARCHAR(200)    NULL     COMMENT '举办方',
  award_date       DATE            NULL     COMMENT '获奖日期',
  award_level      VARCHAR(20)     NOT NULL COMMENT 'SCHOOL/CITY/PROVINCE/NATIONAL/INTERNATIONAL',
  prize            VARCHAR(20)     NOT NULL COMMENT 'FIRST/SECOND/THIRD/EXCELLENT/PARTICIPATION',
  advisor_name     VARCHAR(100)    NULL     COMMENT '指导老师姓名',
  created_by       BIGINT UNSIGNED NULL     COMMENT '录入人',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_awards_date  (award_date),
  KEY idx_awards_level (award_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='获奖记录';


-- ---------------------------------------------------------------------
-- 12. award_students  获奖-学员关联【含公开意愿，关键设计】
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS award_students (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  award_id   BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL COMMENT '获奖学员',
  is_public  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '该学员是否同意对外公开',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_award_student (award_id, user_id),
  KEY idx_as_user (user_id),
  CONSTRAINT fk_as_award FOREIGN KEY (award_id) REFERENCES awards (id) ON DELETE CASCADE,
  CONSTRAINT fk_as_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='获奖-学员关联（含公开意愿）';


-- ---------------------------------------------------------------------
-- 13. award_images  获奖证书照片
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS award_images (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  award_id   BIGINT UNSIGNED NOT NULL,
  image_path VARCHAR(255)    NOT NULL COMMENT '图片存储路径',
  sort_order INT             NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_aimg_award (award_id),
  CONSTRAINT fk_aimg_award FOREIGN KEY (award_id) REFERENCES awards (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='获奖证书图片';


-- ---------------------------------------------------------------------
-- 14. news  新闻与公告
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS news (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title        VARCHAR(200)    NOT NULL COMMENT '标题',
  category     VARCHAR(20)     NOT NULL COMMENT 'ACTIVITY 活动 / NOTICE 通知 / CONTEST 比赛 / TECH 技术分享',
  cover_image  VARCHAR(255)    NULL     COMMENT '封面图',
  content      LONGTEXT        NOT NULL COMMENT '正文（富文本/HTML）',
  status       VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / PUBLISHED 已发布',
  is_top       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否置顶',
  is_internal  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否仅登录可见',
  author_id    BIGINT UNSIGNED NULL     COMMENT '发布人',
  published_at DATETIME        NULL     COMMENT '发布时间',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_news_list (status, is_top, published_at),
  KEY idx_news_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻与公告';


-- ---------------------------------------------------------------------
-- 15. news_images  新闻正文配图
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS news_images (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  news_id    BIGINT UNSIGNED NOT NULL,
  image_path VARCHAR(255)    NOT NULL,
  sort_order INT             NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_nimg_news (news_id),
  CONSTRAINT fk_nimg_news FOREIGN KEY (news_id) REFERENCES news (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻配图';


-- ---------------------------------------------------------------------
-- 16. attendance_events  考勤活动
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_events (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title       VARCHAR(100)    NOT NULL COMMENT '活动标题，如 例行活动',
  event_date  DATE            NOT NULL COMMENT '活动日期',
  type        VARCHAR(30)     NOT NULL COMMENT 'WEEKLY_MEETING 例行活动(必到) / DAILY_OPEN 日常开放(自愿)',
  description VARCHAR(500)    NULL,
  created_by  BIGINT UNSIGNED NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_att_event (event_date, type),
  KEY idx_att_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤活动';


-- ---------------------------------------------------------------------
-- 17. attendance_records  考勤记录【代打留痕】
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_records (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id     BIGINT UNSIGNED NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL COMMENT '学员',
  status       VARCHAR(20)     NOT NULL DEFAULT 'PRESENT' COMMENT 'PRESENT 到场 / ABSENT 缺席 / LEAVE 请假',
  checkin_type VARCHAR(20)     NOT NULL DEFAULT 'SELF' COMMENT 'SELF 自助打卡 / ADMIN 代打卡',
  operator_id  BIGINT UNSIGNED NULL COMMENT '代打卡的操作人（自助时为空）',
  checkin_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_att_record (event_id, user_id),
  KEY idx_attr_user (user_id),
  CONSTRAINT fk_attrec_event FOREIGN KEY (event_id) REFERENCES attendance_events (id) ON DELETE CASCADE,
  CONSTRAINT fk_attrec_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录';


-- ---------------------------------------------------------------------
-- 18. upload_files  上传文件登记表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS upload_files (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  original_name VARCHAR(255)    NOT NULL COMMENT '原始文件名',
  stored_path   VARCHAR(255)    NOT NULL COMMENT '服务器存储路径',
  content_type  VARCHAR(100)    NULL,
  size_bytes    BIGINT          NOT NULL DEFAULT 0,
  uploader_id   BIGINT UNSIGNED NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_upload_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件登记';


-- ---------------------------------------------------------------------
-- 19. password_reset_requests  忘记密码申请（T-012B）
--     2026-10-04 新增（项目负责人已批准）
--
--     【为什么需要这张表】
--       "忘记密码"的流程是【申请 + 社长审核】，不是"重置"：
--       学员提交想设置的新密码 → 进入 PENDING → 社长审核通过后才写回 users.password_hash。
--       在审核通过之前，申请必须有个地方存着，所以需要这张表。
--
--     【设计要点】
--       · new_password_hash 存的是 BCrypt 哈希（提交时就哈希，绝不存明文）
--       · reviewed_by 是【弱引用】（审核人），按 docs/05 原则 6 故意不建外键
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_requests (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL COMMENT '申请人',
  new_password_hash VARCHAR(100)    NOT NULL COMMENT '想设置的新密码（BCrypt 哈希，绝不存明文）',
  status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审核 / APPROVED 通过 / REJECTED 拒绝',
  reviewed_by       BIGINT UNSIGNED NULL COMMENT '审核人社长（弱引用，不建外键）',
  reviewed_at       DATETIME        NULL COMMENT '审核时间',
  review_note       VARCHAR(255)    NULL COMMENT '审核备注（可选）',
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_prr_user (user_id),
  KEY idx_prr_status (status),
  CONSTRAINT fk_prr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='忘记密码申请（申请+审核，非重置）';


SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 建表完成。下一步执行 02-seed.sql 写入初始数据。
-- =====================================================================
