# task-012B · 忘记密码（提交申请 + 社长审核）

| 项 | 值 |
|---|---|
| 任务编号 | **T-012B** |
| 任务名 | 忘记密码 —— 学员提交申请、社长审核通过后新密码生效 |
| 页面 | `/forgot`（申请页）+ `/admin/students` 或独立审核区（后台审核） |
| 对应 | M1 账号与登录（FR-1.4） |
| 状态 | ✅ **已完成**（2026-10-04 14:13 实测通过；原「⬜ 待做」→ 完成记录见 §7） |

---

## 1. 目标（这个任务要达成什么）

学员忘记密码时，**不需要找社长手动改**，而是：

```
登录页点「忘记密码」
  → 申请页：填 学号 / 用户名 / 手机号（任一）+ 想设置的新密码
  → 提交后进入【待社长审核】状态
  → 社长在后台审核【通过】→ 新密码才生效（学员可用新密码登录）
  → 社长【拒绝】→ 申请作废，旧密码继续有效
```

**关键定性（务必守住）**：本功能是「**申请 + 审核**」，**不是「重置」**。
- 页面上**不许出现"重置"二字**
- 社长**看不到也不该设置**学员的新密码明文（申请里已经是学员自己设的）
- 审核动作只决定「是否让这个新密码生效」

## 2. 非目标（明确不做什么）

- ❌ 短信 / 邮箱验证码找回（`docs/01` §1.4 明确不做）
- ❌ 社长手动重置密码为随机串（**已取消**，2026-10-02）
- ❌ 密保问题
- ❌ 学员自助直接生效（**必须经过审核**）
- ❌ **不改数据库结构**（首选方案：复用 `users.password_hash`，仅在审核通过时写入新值）

## 3. 计划修改的文件（待实现时确认）

| 文件 | 动作 |
|---|---|
| `sql/01-schema.sql` | ✅ **已改**（新增第 19 张表 `password_reset_requests`） |
| `sql/03-add-password-reset-requests.sql` | ✅ **已新建并已执行**（幂等增量脚本，见 §7 完成记录） |
| `entity/PasswordResetRequest.java` | **新建**（映射第 19 张表） |
| `repository/PasswordResetRequestRepository.java` | **新建**（按 status / user_id 查） |
| `controller/ForgotPasswordController.java` | **新建**（GET/POST `/forgot`） |
| `controller/AdminPasswordResetController.java` | **新建**（后台审核：通过 / 拒绝） |
| `templates/forgot.html` | **新建**（设计稿：`docs/UI出图/PNG原图/20-忘记密码-提交申请.png`） |
| `templates/login.html` | 改：加「忘记密码」入口 |
| `templates/admin/students.html`（或新页） | 改：显示待审核的改密申请 |
| `config/SecurityConfig.java` | 改：`/forgot` 加入 `permitAll` 白名单 |
| `docs/01`·`docs/spec.md`·`docs/plan.md`·`docs/09` | ✅ 已同步（2026-10-04） |

## 4. 实现步骤（草案）

1. 定表结构（见 §5 待定项 1）→ **改表结构必须先经项目负责人批准**
2. 写实体 + 仓库
3. 写 `/forgot`：页面 + 提交（校验账号存在、两次密码一致、长度 ≥ 8）
4. 提交成功 → 状态 `PENDING`，页面切成"申请已提交"态
5. 后台：列出 `PENDING` 申请 → 「通过」（写 `users.password_hash`）/「拒绝」
6. 审核通过后：学员用新密码登录成功；旧密码失效
7. 重复提交同一账号：**只保留最新一条**（或友好提示"已有待审核申请"）

## 5. 风险与不确定点

| # | 事项 | 说明 |
|---|---|---|
| 1 | ✅ **表结构已批准（2026-10-04）** | 项目负责人选定 **A 方案：新增 `password_reset_requests` 表**。已完成：① 写入 [`sql/01-schema.sql`](../../sql/01-schema.sql)（第 **19** 张表）；② 新增幂等增量脚本 [`sql/03-add-password-reset-requests.sql`](../../sql/03-add-password-reset-requests.sql)；③ 同步 [docs/05 数据库设计](../05-数据库设计说明书.md) §4.10 数据字典。<br>⚠️ ~~**数据库里还没执行**这个脚本~~ → 📌 **2026-10-04 更新：该脚本已执行**（线上库原本没有 `password_reset_requests` 表，执行后已有；详见 §7 完成记录） —— ~~执行前需再确认（属改数据库结构）~~（已确认） |
| 2 | 表结构（已定） | `id` / `user_id`（外键→users，CASCADE）/ `new_password_hash`（BCrypt）/ `status`（PENDING·APPROVED·REJECTED）/ `reviewed_by`（弱引用，**不建外键**）/ `reviewed_at` / `review_note` / `created_at`；索引 `idx_prr_user`、`idx_prr_status`；**无唯一约束**（同一人可有多条历史） |
| 3 | 🔒 **最容易做错的一点** | `new_password_hash` 在**审核通过之前绝不能写回 `users.password_hash`**，否则就等于"学员自己改密码立即生效"、绕过了审核 |
| 4 | 重复提交怎么办 | 一期约定：提交新申请时，把该人**旧的 `PENDING` 置为 `REJECTED`**（只保留最新一条待审核） |
| 5 | 谁能审核 | 与「审核注册申请」同一层：指导老师 / 正社长 / 副社长（**副社长可否审核待定**，实现前确认） |
| 6 | 防滥用 | 同一账号短时间反复提交是否限流（一期可先不加，记入 `TASKS.md`） |
| 7 | 设计稿已有 | ✅ `docs/UI出图/PNG原图/20-忘记密码-提交申请.png`（含 4 个交互状态） |

## 6. 验证命令

> ✅ **实现后已执行**（实测结果见 §7；当时用的是 `mvn -o -B -ntp compile` + 独立实例冒烟 + 逐条 HTTP/登录行为实测）。

```cmd
cd /d D:\Project\FountainHub
mvn -o -B -ntp compile
mvn -o -B -ntp spring-boot:run "-Dspring-boot.run.fork=false"
:: 冒烟：/forgot 200、输入框齐全、action=/forgot、登录页有「忘记密码」入口
:: 行为：提交申请后旧密码仍可登录 / 新密码不可登录；审核通过后新密码可登录、旧密码失效；审核拒绝后原密码继续有效
:: 权限：学员、组长访问后台审核接口 → 403
```

## 7. 完成记录

**状态**：✅ **已完成**（2026-10-04 14:13 实测通过）

### 2026-10-04 · 实现与实测

**改动文件**

| 文件 | 内容 |
|---|---|
| `sql/03-add-password-reset-requests.sql` | **已执行**（建表脚本）—— 线上库原本**没有** `password_reset_requests` 表，执行后已有 |
| `entity/PasswordResetRequest.java` + `repository/PasswordResetRequestRepository.java` | 新建 |
| `controller/ForgotPasswordController.java` | 新建（`GET` / `POST /forgot`） |
| `controller/AdminPasswordResetController.java` | 新建（后台审核：通过 / 拒绝） |
| `templates/forgot.html` | 新建，**4 个状态齐全**：空闲 / 提交中 / 字段级错误 / 提交成功 |
| `templates/login.html` | 改：加「忘记密码」入口 |
| `templates/admin/students.html` | 改：加「待审核的改密申请」区块 |
| `AdminStudentController` / `config/SecurityConfig` | 改：传参并放行 `/forgot` |

**按项目负责人的两个决定**

1. **副社长可以审核** —— 权限写为 `hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')`；
2. **不另做审核页** —— 入口就在「入社申请」审核同一页 `/admin/students`。

**🔒 最关键规则的实测结果**

| 场景 | 结果 |
|---|---|
| 提交申请后，用**旧密码**登录 | ✅ 还能登录 |
| 提交申请后，用**新密码**登录 | ✅ 登不进去 |
| 审核**通过**后 | ✅ 新密码能登录、旧密码失效 |
| 审核**拒绝**后 | ✅ 原密码继续有效、被拒的密码登不进去 |
| 学员 / 组长访问审核接口 | ✅ **403** |

**验证输出**

- 测试脚本：**18 通过 / 7 失败**（失败全因一个模板 bug，已修）；修完复验 `/forgot` **200**、输入框齐全、`action=/forgot`、**无手机号**、日志 **ERROR 0 条**。
- ⚠️ **教训（第 11 次踩同一个坑）**：`forgot.html` 里把 `@{/forgot}` 写成了 `${/forgot}`（**3 处全错**）→ 页面直接 **500**（`SpelParseException`）。

**测试数据**

- `password_reset_requests` 表留有测试申请 —— ⚠️ DEVLOG 当时写「4 条」，**2026-10-04 只读实测为 3 条**（APPROVED / REJECTED / APPROVED），**以实测为准**。

> 📌 2026-10-04 文档 ↔ 实际项目对齐时复核并回填本记录（只改文档、不动代码）。
