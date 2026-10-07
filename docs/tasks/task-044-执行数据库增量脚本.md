# task-044 · 执行数据库增量脚本（把新表补进现有库）

| 项 | 值 |
|---|---|
| 任务编号 | **T-044** |
| 任务名 | 给**已建好的** `fountainhub` 库执行两个增量脚本，补上第 19、20 张表 |
| 类型 | 🔧 **运维 / 数据库结构变更**（不写业务代码） |
| 状态 | ✅ **已完成**（2026-10-04 实测：库中 **20 张表**，两个脚本均已执行；原「⏸️ 待执行」） |
| 依据 | [sql/03-add-password-reset-requests.sql](../../sql/03-add-password-reset-requests.sql)、[sql/04-add-site-settings.sql](../../sql/04-add-site-settings.sql) |
| ⚠️ 前置 | **属"改数据库结构"，按 `AGENTS.md` §7 必须先经项目负责人确认**（脚本已获批，执行动作仍需确认） |

---

## 1. 目标（为什么必须做）

代码里已经有**两个实体在映射这两张新表**，而**当前库里还是 10-02 建的那 18 张表**（📌 **执行前**的状态；执行后现为 **20 张表**）。
项目配置是 `spring.jpa.hibernate.ddl-auto: validate` —— **它只校验、不建表**。
→ **只要表不存在，应用启动就会直接失败**（`Schema-validation: missing table [...]`）。

| 新表 | 对应实体 / 用途 | 谁在用 |
|---|---|---|
| `password_reset_requests` | `entity/PasswordResetRequest.java` —— 忘记密码申请（T-012B） | `/forgot`、后台审核 |
| `site_settings` | `entity/SiteSetting.java` —— 站点级配置（T-042 首页背景图） | `/admin/settings`、`/background` |

> ⚠️ 作者注：2026-10-04 项目负责人曾决定"背景图暂用白色、不建表"；但代码里最终**建了** `site_settings`
> （`sql/04` 标注批准日期 2026-10-05，实体注释也写了"映射第 20 张表"）。
> **以当前代码与脚本为准** —— 本任务就是把这两张表补进库。

---

## 2. 非目标

- ❌ 不重建整个库（**绝不**执行 `DROP DATABASE`；`01-schema.sql` 顶部那段 DROP 保持注释状态）
- ❌ 不重跑 `01-schema.sql` / `02-seed.sql`（**非幂等，会中断或污染数据**）
- ❌ 不改任何业务代码、不改 `application*.yml`
- ❌ 不动已有 18 张表的任何数据
- ❌ 不做数据库备份策略改造（那是另一件事）

---

## 3. ⚠️ 先备份（执行前必做）

```cmd
:: 用全路径（mysql 不在 PATH）
"C:\Program Files\MySQL\MySQL Server 26.7\bin\mysqldump.exe" --default-character-set=utf8mb4 -u root -p fountainhub > D:\backup\fountainhub-before-T044.sql
```

（目录不存在就先建；**这条命令会提示输入密码，不要写进任何文件**。）

---

## 4. 执行步骤

### 步骤 1 · 执行第一个增量脚本（忘记密码申请表）

```cmd
"C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p fountainhub < D:\Project\FountainHub\sql\03-add-password-reset-requests.sql
```

### 步骤 2 · 执行第二个增量脚本（站点配置表）

```cmd
"C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p fountainhub < D:\Project\FountainHub\sql\04-add-site-settings.sql
```

> 两个脚本都**幂等**（`CREATE TABLE IF NOT EXISTS`），**重复执行不会报错**，可以放心。

### 步骤 3 · 验证表已存在

```cmd
"C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p fountainhub -e "SHOW TABLES;"
```

**预期**：共 **20 张表**，其中包含
`password_reset_requests` 与 `site_settings`。

### 步骤 4 · 逐列核对（防止字段名/类型与实体不一致）

```cmd
"C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p fountainhub -e "DESC password_reset_requests; DESC site_settings;"
```

**预期字段**：

| password_reset_requests | site_settings |
|---|---|
| id, user_id, new_password_hash, status, reviewed_by, reviewed_at, review_note, created_at | setting_key, setting_value, updated_at |

### 步骤 5 · 启动应用验证（这一步才算真的过）

```cmd
cd /d D:\Project\FountainHub
mvn -o -B -ntp spring-boot:run
```

**成功标志（控制台）**：

```
HikariPool-1 - Start completed.
Initialized JPA EntityManagerFactory          ← 这一行出现 = validate 通过
Tomcat started on port 8080 (http)
Started FountainHubApplication in x.xxx seconds
```

**失败标志（就是本任务要解决的问题）**：

```
Schema-validation: missing table [password_reset_requests]
Schema-validation: missing table [site_settings]
```

---

## 5. 风险与不确定点

| # | 事项 | 说明 |
|---|---|---|
| 1 | ⚠️ **改数据库结构** | 按 `AGENTS.md` §7，**必须先经项目负责人确认**才能执行 |
| 2 | **外键依赖** | `password_reset_requests.user_id` 外键指向 `users(id)` → 要求 `users` 表已存在（它当然在） |
| 3 | **两个脚本的执行顺序** | 无相互依赖，先后随意 |
| 4 | **回滚** | `DROP TABLE IF EXISTS password_reset_requests;` / `DROP TABLE IF EXISTS site_settings;`；或从 §3 的备份恢复 |
| 5 | **`mysql` 不在 PATH** | 必须写全路径 `C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe` |
| 6 | **不确定是否已经执行过** | 若你已执行过，`SHOW TABLES` 会直接看到这两张表 → 那本任务可直接标记 ✅ |
| 7 | **`sql/01-schema.sql` 是否也已同步 site_settings** | 需核对：新装机跑 `01-schema.sql` 时应能一次建出全部 **20** 张表（若还没加，需补进去） |

---

## 6. 完成记录

**状态**：✅ **已完成**（2026-10-04）

### 2026-10-04 · 执行与实测

**执行内容**

| 脚本 | 结果 |
|---|---|
| `sql/03-add-password-reset-requests.sql` | ✅ **已执行**（第 **19** 张表 `password_reset_requests`） |
| `sql/04-add-site-settings.sql` | ✅ **已执行**（第 **20** 张表 `site_settings`） |

**2026-10-04 只读实测**

- 库中 **20 张表**；
- `password_reset_requests` **3 行**、`site_settings` **1 行**。

**任务书自定的三个判定条件 —— 全部满足**

| # | 条件 | 结果 |
|---|---|---|
| ① | `SHOW TABLES` 能看到两张新表 | ✅ |
| ② | `DESC` 字段与实体一致 | ✅ 依据：应用以 `spring.jpa.hibernate.ddl-auto=validate` 启动**无 `Schema-validation` 报错** |
| ③ | 应用能正常启动 | ✅ 8081 实例实测 `/ping` → pong、各页面 200 |

**说明**

- 本次为**只读复核**，**未重复执行**脚本（两个脚本都是**幂等**的，重复执行也安全）。

**原判定条件（保留备查）**：① `SHOW TABLES` 能看到两张新表；② `DESC` 字段与实体一致；③ **应用能正常启动且无 `Schema-validation` 报错**。

> 📌 2026-10-04 文档 ↔ 实际项目对齐时复核并回填本记录（只改文档、不动代码）。
