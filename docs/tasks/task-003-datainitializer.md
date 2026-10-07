# task-003 · DataInitializer（初始管理员）

| 项 | 值 |
|---|---|
| 任务编号 | **T-003** |
| 任务名 | `DataInitializer`：`users` 表为空时创建初始管理员（`admin` / TEACHER） |
| 对应 | **里程碑 2 收尾**（数据库 ↔ Java 正式打通） |
| 状态 | ✅ **已完成**（2026-10-02） |
| 依据 | 00-AI交接说明 第 134~136 行；[sql/02-seed.sql](../../sql/02-seed.sql) 第 7~17 行 |

---

## 1. 目标

应用启动时，**若 `users` 表为空**，创建初始管理员：

| 字段 | 值 |
|---|---|
| 用户名 | `admin` |
| 密码 | 由**环境变量**提供 —— **必须存 BCrypt 哈希** |
| 角色 | `TEACHER`（指导老师） |
| real_name | `指导老师`（文档未规定，本次决定） |
| student_no / phone / group_id / last_login_at | 全部 NULL |

**已向项目负责人说明并获批准**：为此引入一个最小依赖 `spring-security-crypto`（只含加密工具类，**不含** Spring Security 自动配置）。

---

## 2. 非目标（本次明确不做）

- ❌ 不启用登录拦截（T-004）
- ❌ 不写登录页 / 登录逻辑（T-005 / T-006）
- ❌ 不改数据库表结构
- ❌ 不创建测试学员账号（sql/02-seed.sql 建议由后台页面手工创建）
- ❌ 不写改密码功能（T-008）
- ❌ **不加 `existsBy*`** —— 用 `JpaRepository` 自带的 `count()` 就够

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `pom.xml` | 改：新增 `spring-security-crypto` |
| `src/main/java/com/fountainhub/config/DataInitializer.java` | 新建 |
| `docs/tasks/task-003-datainitializer.md` | 本文件 |
| `docs/tasks/task-002-userrepository.md` + `docs/09-开发进度日志.md` | 改：**更正一处错误说法**（见第 7 节） |
| `docs/plan.md` | T-003 → ✅ |

---

## 4. 实现步骤

1. `pom.xml` 加 `spring-security-crypto`（**不写版本**，由父 POM 管理为 6.3.4）
2. 新建 `com.fountainhub.config.DataInitializer`，实现 `CommandLineRunner`
3. `run()` 第一步用 `count()` 做幂等保护：不是 0 就直接 return
4. 用 `BCryptPasswordEncoder().encode(...)` 把密码转哈希后 `save`
5. 编译 → 启动两次验证幂等 → 文档更新

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | 本项目第一次往数据库写数据 | `count()==0` 保护 + 启动前实测 `users` 为 0 |
| 2 | 新增依赖可能带来登录拦截 | **实测 `/ping` 仍返回 200 pong** ✅ |
| 3 | 初始密码明文已写在 docs/00 与 sql/02-seed.sql 注释里（**既成事实**） | 上线后第一件事改密码（T-008） |
| 4 | `count()==0` 是唯一防线：若库里先有学员却没管理员，会跳过而不补建 | 按 spec 字面执行，已知悉 |
| 5 | 我此前一处说法有误 | 见第 7 节「更正」 |

---

## 6. 验证命令

见第 7 节（已实际执行）。数据库侧的命令由项目负责人本人在本机执行（涉及 MySQL 密码）。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `pom.xml` | 改：+ `spring-security-crypto`（6.3.4） | — |
| `src/main/java/com/fountainhub/config/DataInitializer.java` | 新建 | 3238 字节 |
| 三个文档 | 新建 / 更正 / 状态更新 | — |

### 验证结果（真实输出）

| 验证 | 结果 |
|---|---|
| `mvn -B -ntp compile` | ✅ `BUILD SUCCESS`，**7** 个源文件（T-002 时是 6 个） |
| **第一次启动** | ✅ 日志出现：`检测到 users 表为空，正在创建初始管理员 ...` → `初始管理员已创建：用户名 admin（角色 指导老师）` |
| **`/ping` 是否被拦截** | ✅ **HTTP 200 pong**（**不是** 302 跳登录页）→ 证明 `spring-security-crypto` 没有引入安全自动配置 |
| **第二次启动（幂等性）** | ✅ 日志出现：`users 表已有 1 个用户，跳过初始化`，**没有重复创建** |

### 遇到的问题（如实记录）

| # | 问题 | 根因 | 处置 |
|---|---|---|---|
| 1 | 首次 `mvn compile` 报 `AccessDeniedException: ...\\.m2\\repository\\...\\spring-security-crypto` | 该命令跑在 DSH 沙箱里，沙箱不允许写工作区之外的 `C:\Users\<你的用户名>\.m2`（**不是** ACL 问题，实测 ACL 正常） | 重试即成功 —— 依赖 jar 其实已下载完整（97.8 KB），第二次只需读缓存 |

### ⚠️ 更正 T-002 的一处错误说法

T-002 完成记录里写的「**留给 T-003**（`DataInitializer` 会真正调用 `findByUsername` / `findByStudentNo` / `findByPhone`）」**不准确**：

- T-003 用的是 `JpaRepository` 自带的 `count()`，**并没有调用**那三个派生查询方法
- 这三个方法真正被使用、并得到"能查出数据"验证的地方是 **T-006（三种登录方式）**

已在 `task-002-userrepository.md` 与 `docs/09-开发进度日志.md` 中同步更正。

### 遗留与下一步

- ⏸️ `findBy*` 三个方法的"真实数据"验证 → **T-006**
- ⚠️ 当时初始密码以明文写在 docs/00 与 sql/02-seed.sql 里（**2026-10-04 已改为读环境变量 `FH_INIT_PASSWORD`**）→ 上线后仍须立即改密码
- ➡️ 下一步：**T-004** 启用 Spring Security（放开依赖、基础配置、静态资源放行）—— 对应 `plan.md` §4 待商定项 **Q2b**（兼后勤社长的权限）**必须先定**
