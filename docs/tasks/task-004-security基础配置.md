# task-004 · 启用 Spring Security（基础配置 + 静态资源放行）

| 项 | 值 |
|---|---|
| 任务编号 | **T-004** |
| 任务名 | 启用 Spring Security：放开依赖、基础配置、静态资源放行 |
| 对应 | **M1 阶段 1**（账号与登录）；对应 [plan.md](../plan.md) 阶段 1 |
| 状态 | ✅ **已完成**（2026-10-02） |
| 前置 | ✅ Q2b 已确认（兼后勤社长权限与其他社长完全一致，见 [01-需求规格说明书](../01-需求规格说明书.md) §3.2） |

---

## 1. 目标

把 Spring Security 从"注释状态"变成"运行状态"，并配好**谁不用登录**：

- 放开两个依赖：`spring-boot-starter-security`、`thymeleaf-extras-springsecurity6`
- 新建 `SecurityConfig`：定义**放行白名单** + `PasswordEncoder` Bean
- 关掉 Spring 自带的默认内存用户（安全考虑）

**⚠️ 本任务最关键的认知**：启用后**所有请求默认需要登录**，白名单漏写一个路径，那个页面游客就看不了了。

---

## 2. 非目标（本次明确不做）

- ❌ 不写登录页（T-005）
- ❌ 不写 `UserDetailsService` / 三种登录方式（T-006）
- ❌ 不写角色规则（`/admin/**` 的角色限制）与 403 页面（T-007）
- ❌ 不写改密码（T-008）
- ❌ 不改数据库、不动业务代码（唯一例外：`DataInitializer` 改用注入的 encoder）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `pom.xml` | 改：取消注释 Security 两个依赖 |
| `src/main/java/com/fountainhub/config/SecurityConfig.java` | 新建 |
| `src/main/java/com/fountainhub/config/DataInitializer.java` | 改：注入 `PasswordEncoder` |
| `src/main/resources/application.yml` | 改：排除 `UserDetailsServiceAutoConfiguration` |
| `docs/tasks/task-004-security基础配置.md` | 本文件 |
| `docs/plan.md` / `docs/09-开发进度日志.md` | 更新 |

---

## 4. 实现步骤

1. `pom.xml` 解开 Security 两个依赖的注释
2. 新建 `SecurityConfig`：`PasswordEncoder` Bean + `SecurityFilterChain`
3. 白名单：`/`、`/ping`、`/about`、`/news/**`、静态资源、`/login`、`/logout`、`/error`；其余 `authenticated()`
4. `application.yml` 加 `spring.autoconfigure.exclude`
5. 编译 → 启动 → 逐条实测 6 个地址
6. 更新文档

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | 白名单漏写 → 现有页面跳登录页 | 实测 6 条（见第 7 节），全部符合预期 |
| 2 | Spring 自动造"默认内存用户"，随机密码打印在日志里 | 已在 `application.yml` 排除；**实测日志无 `generated security password`** ✅ |
| 3 | `/error` 不放行会导致"报错看起来像没登录" | 已放行，且注释写明原因 |

---

## 6. 验证命令

`mvn -B -ntp compile` → `mvn -B -ntp spring-boot:run` → 访问下面 6 个地址。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 |
|---|---|
| `pom.xml` | 启用 `spring-boot-starter-security` + `thymeleaf-extras-springsecurity6` |
| `SecurityConfig.java` | 新建（2559 字节） |
| `DataInitializer.java` | 改为注入 `PasswordEncoder`（不再 `new`） |
| `application.yml` | 加 `autoconfigure.exclude` |

### 验证结果（真实输出）

`mvn compile` → ✅ `BUILD SUCCESS`，**8** 个源文件（T-003 时是 7 个）

启动后逐条实测（Node 直连，**不自动跟随重定向**，这样才看得见 302）：

| 地址 | 真实结果 | 判定 |
|---|---|---|
| `/ping` | HTTP **200** 正文 `pong` | ✅ 已放行 |
| `/` | HTTP **200** | ✅ 公开首页游客可看 |
| `/admin/students` | HTTP **302** → `/login` | ⭐ **Security 生效的铁证** |
| `/css/x.css` | HTTP **404**（不是 302） | ✅ 静态资源已放行 |
| `/me` | HTTP **302** → `/login` | ✅ 需登录 |
| `/news` | HTTP **404**（不是 302） | ✅ 已放行（页面 T-023 才做） |
| 启动日志 `Using generated security password` | **未出现** | ✅ 默认内存用户已关闭 |

### 遇到的问题（如实记录）

| # | 问题 | 根因 | 处置 |
|---|---|---|---|
| 1 | 首次 `mvn compile` 报 `AccessDeniedException: ...\.m2\...\spring-boot-starter-security` | DSH 沙箱不允许写工作区之外的 `.m2`（下载新依赖要写盘） | **重试第 3 次成功** —— Maven 分几次把依赖下完（第 2 次还卡在 `.pom.lastUpdated`） |
| 2 | PowerShell 读不出 302 的详情（报"对象的当前状态使该操作无效"） | `Invoke-WebRequest -MaximumRedirection 0` 在 302 时的已知怪行为 | 改用 **Node `http.get`** 直连（默认不跟随重定向），结果清晰 |

### 遗留与下一步

- ⏸️ 登录页目前是 **Spring 自带的默认页**（很朴素）→ **T-005** 换成自己的 Thymeleaf 页面
- ⏸️ 目前**没有任何账号能登录成功**（还没有 `UserDetailsService`）→ **T-006**
- ⏸️ 角色规则（只有社长能进 `/admin`）尚未生效 → **T-007**
- ➡️ 下一步：**T-005** 登录页
