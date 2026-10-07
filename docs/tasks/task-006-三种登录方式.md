# task-006 · 三种登录方式（学号 / 用户名 / 手机号 + 密码）

| 项 | 值 |
|---|---|
| 任务编号 | **T-006** |
| 任务名 | 三种登录方式：学号 / 手机号 / 用户名 + 密码（自定义 `UserDetailsService`） |
| 对应 | **M1 阶段 1** |
| 状态 | ✅ **已完成**（2026-10-02） |
| 里程碑意义 | **第一步能真正登录进去** 🎉 |

---

## 1. 目标

写 `DbUserDetailsService`，让登录真正能验证：

> 用户在「账号」框输入的字符 → **依次按 用户名 → 学号 → 手机号** 查 → 查到人 + 密码对 + 状态允许 → **登录成功**

⭐ 这是 T-002 写的三个查询方法**第一次被真正调用**。

### 四个关键设计决定

| # | 决定 | 为什么 |
|---|---|---|
| 1 | 用 Spring 内置的 `User`（不自己写 `UserDetails` 类） | 最小改动；**username 统一设成数据库里的 username**，不用管用户是用哪种方式登录的 |
| 2 | 权限名 = `ROLE_` + 枚举名 | Spring 的 `hasRole("TEACHER")` 会自动找 `ROLE_TEACHER`，两边必须对齐 |
| 3 | **允许登录的状态：`ACTIVE` + `GRADUATED`** | 项目负责人 2026-10-02 决定：毕业生仍可登录回来看记录；**``LEFT``（离社）与 `DISABLED`（禁用）一律拒绝** |
| 4 | 三种方式**依次尝试**，命中一个即可 | `findByUsername → findByStudentNo → findByPhone` |
| 5 | 提示文案**不区分**「账号不存在 / 密码错 / 被禁用」 | **刻意**：能区分的话，别人就能试探"某账号存不存在"（用户枚举漏洞） |

---

## 2. 非目标

- ❌ 不写角色规则（T-007）、不写 403 页（T-007）
- ❌ 不写改密码（T-008）、不写「我的资料」（T-013）
- ❌ 不改数据库表结构、不重做登录页（T-005 已完成）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `src/main/java/com/fountainhub/security/DbUserDetailsService.java` | 新建（新包 `security`） |
| `src/main/resources/application.yml` | 改：删掉 T-004 临时加的 `autoconfigure.exclude` |
| `docs/tasks/task-006-三种登录方式.md` | 本文件 |
| `docs/01-需求规格说明书.md` | 改：新增规则 **R7（账号状态与登录）** |
| `docs/plan.md` / `docs/09-开发进度日志.md` | 更新 |

**SecurityConfig 未改动** —— Spring 发现「一个 `UserDetailsService` Bean + 一个 `PasswordEncoder` Bean」，会自动组装好验证器。

---

## 4. 实现步骤

1. 新建 `DbUserDetailsService`（`@Service`，实现 `UserDetailsService`）
2. `loadUserByUsername`：trim → 三种查询依次尝试 → 组装 `UserDetails`
3. 状态白名单：`Set.of(ACTIVE, GRADUATED)`
4. `application.yml` 删掉 `autoconfigure.exclude`
5. 编译 → 独立实例验证（正确密码 / 错误密码 / 未登录）
6. 更新文档

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | 三种方式**只有「用户名」能端到端测**（`admin` 的学号/手机号是 NULL） | 学号/手机号登录的完整验证留到 **T-011**；**本任务书不谎称测过** |
| 2 | 「禁用账号被拒」也需要第二个账号才能测 | 同上 |
| 3 | 删掉 `exclude` 后是否又冒出默认用户 | **实测启动日志无 `generated security password`** ✅ |
| 4 | 登录成功去哪 | Spring 默认行为（回到原本想访问的页面） |

---

## 6. 验证命令

`mvn -B -ntp compile` → `mvn -B -ntp spring-boot:run` → 用 `admin` / 默认密码登录。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `DbUserDetailsService.java` | 新建 | 3431 字节 |
| `application.yml` | 删掉 8 行（T-004 的 exclude 及注释） | — |
| `docs/01-需求规格说明书.md` | 新增 R7 | — |

### 验证结果（真实输出，独立实例 8081，启动零错误）

`mvn compile` → ✅ `BUILD SUCCESS`，**10** 个源文件（T-005 时是 9 个）

| 验证 | 真实结果 | 判定 |
|---|---|---|
| `POST /login`（admin + **正确密码**） | HTTP **302** → `http://127.0.0.1:8081/` | ⭐ **登录成功** |
| 登录后 `GET /admin/students`（带会话） | HTTP **404** | ✅ 已通过安全校验（页面 T-010 才做） |
| `POST /login`（admin + **错误密码**） | HTTP **302** → `/login?error` | ✅ 被拒 |
| 未登录 `GET /admin/students` | HTTP **302** → `/login` | ✅ 被拦 |
| 启动日志 `generated security password` | **未出现** | ✅ 删掉 exclude 后也没有默认用户 |
| `DataInitializer` | `users 表已有 1 个用户，跳过初始化` | ✅ 幂等仍未破坏 |

### 遗留与下一步

- ⏸️ **学号 / 手机号 登录**、**禁用账号被拒** 的端到端验证 → **T-011**（需要带学号的学员账号）
- ⏸️ 角色规则（谁只能进哪些页面）→ **T-007**
- ➡️ 下一步：**T-007** 角色与授权（6 种角色、`/admin/**` 未登录跳登录页、**越权返回 403**）
