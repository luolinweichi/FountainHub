# task-011 · 学员自助注册 + 管理员审核

| 项 | 值 |
|---|---|
| 任务编号 | **T-011** |
| 任务名 | **自助注册 + 管理员审核**（原计划是"管理员后台建号"，2026-10-02 设计变更） |
| 页面 | `/register`（公开）+ `/admin/students`（审核） |
| 对应 | **M2 阶段 2** |
| 状态 | ✅ **已完成**（2026-10-02） |

---

## 1. 目标

把"账号从哪来"这件事做成闭环：

`游客打开 /register 填表 → 提交 → 账号状态 = 待审核（登不进去）
   → 管理员在学员列表点「通过」→ 转为在读，可以登录
   → 或点「拒绝」→ 该申请被删除`

### 设计变更说明（重要）

| | 原计划 | 现在 |
|---|---|---|
| 谁建账号 | 管理员在后台填表 | **学员自助注册** |
| 初始状态 | 直接 ACTIVE | **PENDING（待审核）** |
| 页面 | `/admin/students/new` | **公开的 `/register` + 列表里的审核按钮** |

依据：[spec.md](../spec.md) §2.1 T1、[01-需求规格说明书](../01-需求规格说明书.md) FR-1.9 / FR-1.10。

### 三条不可动摇的安全设计

| # | 设计 | 为什么 |
|---|---|---|
| 1 | **`role` 一律写死 `STUDENT`** | 若允许注册者自选角色，任何人都能注册一个 `TEACHER` 账号直接拿到后台权限 |
| 2 | **`status` 一律写死 `PENDING`** | 注册不能直接获得"能登录"的状态，必须过审核 |
| 3 | 审核接口**只处理 `PENDING` 的账号** | 防止有人拿它去改/删一个正式学员 |

### 为什么 `PENDING` 能"自动"登不进去

登录白名单在 [DbUserDetailsService](../../src/main/java/com/fountainhub/security/DbUserDetailsService.java) 里是 `{ACTIVE, GRADUATED}`，
`PENDING` 天然不在其中 —— **一行权限代码都不用改**。

---

## 2. 非目标

- ❌ **小组选择**（需要 `StudyGroup` 实体，T-015 的事）→ T-016
- ❌ 角色选择（**永远 STUDENT**）
- ❌ 管理员"直接建号"（FR-1.1 已降为 P1，一期先靠自助注册）
- ❌ 验证码 / 邮箱验证（二期再说）
- ❌ 头像上传（FR-2.8，P2）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `entity/UserStatus.java` | 改：加 `PENDING("待审核")` |
| `config/SecurityConfig.java` | 改：放行 `/register` |
| `controller/RegisterController.java` | 新建 |
| `templates/register.html` | 新建 |
| `controller/AdminStudentController.java` | 改：加 `/{id}/approve` 与 `/{id}/reject` |
| `templates/admin/students.html` | 改：加「操作」列 + 待审核标签样式 |
| 文档（spec / 01 / 00 / plan）+ 本任务书 | 改 |

---

## 4. 实现步骤

1. **先改文档**（SDD：先文档后代码）—— spec §2.1、docs/01 FR-1.9/1.10、docs/00 §6、plan.md T-011
2. `UserStatus` 加 `PENDING`；`SecurityConfig` 放行 `/register`
3. 新建 `RegisterController`（GET 表单 / POST 建账号 + 建档案，`@Transactional`）
4. 新建 `register.html`
5. `AdminStudentController` 加通过 / 拒绝
6. `students.html` 加操作列
7. 编译 → 端到端验证（**含两个安全测试**）
8. 更新文档

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | 🔒 注册者自选角色 → 提权 | 服务端写死 `STUDENT`；**实测注入 `role=TEACHER` 无效** ✅ |
| 2 | 🔒 注册即获得登录能力 | 服务端写死 `PENDING`；**实测登不进去** ✅ |
| 3 | 🔒 手机号被展示 | 列表页全程不查不显示；**实测 3 个 URL 的 HTML 无手机号** ✅ |
| 4 | 注册写两张表可能留下半成品 | `@Transactional`：要么都成功，要么都回滚 |
| 5 | 公开注册页可能被灌垃圾 | 三列都有 UNIQUE 约束挡住重复；无验证码（校内使用，可接受，二期再加） |

---

## 6. 验证命令

`mvn -B -ntp compile` → `mvn -B -ntp spring-boot:run` → 浏览器打开 `/register` 走一遍。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `UserStatus.java` | 加 `PENDING` + 修正 GRADUATED 的注释 | — |
| `SecurityConfig.java` | 放行 `/register` | — |
| `RegisterController.java` | 新建 | 8003 字节 |
| `templates/register.html` | 新建 | 5891 字节 |
| `AdminStudentController.java` | 加 approve / reject | — |
| `templates/admin/students.html` | 加操作列 + pending 样式 | — |

### 验证结果（真实输出）

`mvn compile` → ✅ `BUILD SUCCESS`，**17** 个源文件

| 验证 | 真实结果 |
|---|---|
| 未登录 `GET /register` | HTTP **200**，有表单、有 CSRF 字段 ✅ |
| 两次密码不一致 | 页面提示「两次输入的密码不一致」✅ |
| 手机号格式不对 | 页面提示「手机号格式不对」✅ |
| 用户名已存在 | 页面提示「这个用户名已经有人用了」✅ |
| 正常注册 | HTTP **302** → `/register?submitted` ✅ |
| 🔒 **待审核能否登录** | **被拒绝** ✅（登不进去） |
| 管理员待审核列表 | 能看到该申请 ✅ |
| 点「拒绝」 | HTTP 302 → 该申请**被删除**，列表里消失 ✅ |
| 🔒 **安全测试 A：注入 `role=TEACHER&status=ACTIVE`** | 提交后**仍不能登录**；审核通过后访问 `/admin/students` = **403** → **注入被彻底无视，他始终是学员** ✅⭐ |
| 点「通过」 | 转为在读，之后可以登录 ✅ |
| 🔒 **安全测试 B：手机号红线** | `/admin/students`、`?status=PENDING`、`?status=ACTIVE` 三个页面 HTML **均无手机号** ✅ |
| 运行日志错误 | **无** ✅ |

### 遇到的问题（如实记录）

| # | 问题 | 根因 | 处置 |
|---|---|---|---|
| 1 | **同一个 Thymeleaf 错误第 3 次**：`th:action` 里把 `@{...}` 写成 `${...}`，共 5 处 | 我写模板时的机械替换没区分"链接表达式"和"变量表达式" | **幸好我在 T-010 时加了一道全项目模板自动扫描**，写完立刻被抓出来 ✅ 以后每次改模板都跑它 |
| 2 | approve/reject 的 URL 写法错误 | 写成了 `${/admin/students/${u.id}/approve}`，正确写法是 `@{/admin/students/{uid}/approve(uid=${u.id})}` | 已改为路径变量写法，并在注释里写明两种写法的区别 |
| 3 | 我的验证脚本有个变量名笔误崩了 | `gotIn2 is not defined` | 拆成第二次运行补完，结论不受影响 |

---

## 8. 遗留与下一步

- ⏸️ 小组选择 → **T-016**
- ⏸️ 管理员直接建号（FR-1.1）→ P1
- ⚠️ 验证过程留下了一个测试账号 `regtest2`（姓名"测试越权"，学号 T1002，状态=在读）。清理：`DELETE FROM users WHERE username='regtest2';`
- ➡️ 下一步：**T-012** 学员详情与编辑（含账号禁用、角色调整）—— ⚠️ 「社长重置密码」已取消，改为 **T-012B**（暂缓）
