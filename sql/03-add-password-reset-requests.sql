-- =====================================================================
-- FountainHub · 增量脚本 001：新增 password_reset_requests 表
-- 数据库 : MySQL 8.0+
-- 日期   : 2026-10-04
-- 对应   : T-012B 忘记密码（申请 + 社长审核）
-- 批准   : ✅ 项目负责人 2026-10-04 批准（按 docs/05 的 A 方案：新增表）
-- 幂等   : ✅ 是。用 IF NOT EXISTS，可重复执行；表已存在时不会报错。
-- 执行   : "C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p fountainhub < sql\03-add-password-reset-requests.sql
-- 回滚   : DROP TABLE IF EXISTS password_reset_requests;
-- =====================================================================

USE fountainhub;

-- ---------------------------------------------------------------------
-- password_reset_requests  忘记密码申请
--
-- 【为什么需要】"忘记密码"是【申请 + 社长审核】，不是"重置"：
--   学员提交想设置的新密码 → 状态 PENDING → 社长审核通过后才把新哈希写回
--   users.password_hash。审核之前申请要有个地方存着。
--
-- 【设计要点】
--   · new_password_hash 存 BCrypt 哈希（提交时就哈希，绝不存明文）
--   · reviewed_by 是【弱引用】（审核人），按 docs/05 设计原则 6 故意不建外键，
--     避免删用户时连带影响历史记录
--   · user_id 建外键 + ON DELETE CASCADE：人没了，申请也没意义
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

-- ---------------------------------------------------------------------
-- 验证：执行后应当能看到这张表
--   SHOW TABLES LIKE 'password_reset_requests';
--   DESC password_reset_requests;
-- ---------------------------------------------------------------------
