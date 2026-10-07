# task-005 · 登录页（Thymeleaf + Bootstrap）

| 项 | 值 |
|---|---|
| 任务编号 | **T-005** |
| 任务名 | 登录页：Thymeleaf 表单 + Bootstrap 样式 |
| 页面 | `/login` |
| 对应 | **M1 阶段 1** |
| 状态 | ✅ **已完成**（2026-10-02） |

---

## 1. 目标

用自己的登录页替换 Spring 自带的默认页。

**关键设计（T-006 依赖它）**：表单只有两个输入框 ——

| 输入框 | name（必须） | 用户填什么 |
|---|---|---|
| 账号 | `username` | **用户名 / 学号 / 手机号 三选一**（docs/00 第 62 行） |
| 密码 | `password` | 密码 |

`name` 必须是这两个名字：这是 Spring Security 默认读取的参数名。

---

## 2. 非目标

- ❌ 不写登录验证逻辑（T-006）→ **所以本步做完，输什么都还登不进去**
- ❌ 不写注册 / 忘记密码
- ❌ 不写角色跳转（T-007）
- ❌ 不写改密码（T-008）
- ❌ 不改数据库

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `src/main/java/com/fountainhub/controller/LoginController.java` | 新建 |
| `src/main/resources/templates/login.html` | 新建 |
| `src/main/java/com/fountainhub/config/SecurityConfig.java` | 改：`formLogin` 指定 `loginPage("/login")` |
| `docs/tasks/task-005-登录页.md` | 本文件 |
| `docs/plan.md` / `docs/09-开发进度日志.md` | 更新 |

---

## 4. 实现步骤

1. 新建 `LoginController`（`@GetMapping("/login")` → 返回 `login`）
2. 新建 `login.html`：Bootstrap 卡片 + 账号/密码表单 + 错误/退出提示
3. `SecurityConfig` 的 `formLogin` 改为 `.loginPage("/login").permitAll()`
4. 编译 → 启动 → 实测（GET 页面、POST 错密码、未登录访问后台）
5. 更新文档

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | **必须用 `th:action` 而不是 `action`** | Security 默认开 CSRF，`th:action` 才会自动插入 CSRF 隐藏字段；写错会 **403 且报错难懂** |
| 2 | CDN 可能加载不出 | 用国内 bootcdn + 一段"保底内联样式"（Bootstrap 挂了页面依然能正常输入） |
| 3 | 现在输什么都登不进去 | **预期**，T-006 补上 `UserDetailsService` |

---

## 6. 验证命令

`mvn -B -ntp compile` → `mvn -B -ntp spring-boot:run` → 浏览器访问 `/login`。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `LoginController.java` | 新建 | 932 字节 |
| `templates/login.html` | 新建 | 3211 字节 |
| `SecurityConfig.java` | 改：`formLogin` 指向 `/login` | — |

### 验证结果（真实输出）

`mvn compile` → ✅ `BUILD SUCCESS`，**9** 个源文件（T-004 时是 8 个）

在**独立实例**（端口 8081，启动日志零错误：`Tomcat started on port 8081` + `Started FountainHubApplication in 4.015 seconds`）上实测：

| 验证 | 真实结果 |
|---|---|
| `GET /login` | HTTP **200**，3009 字节；含 `name="username"` ✅、`name="password"` ✅、**CSRF 隐藏字段** ✅、Bootstrap CDN ✅、保底内联样式 ✅ |
| `POST /login`（错误密码 + 正确 CSRF token） | HTTP **302** → `/login?error` ✅ |
| `GET /login?error` | HTTP **200**，页面含「账号或密码错误」 ✅ |
| `GET /admin/students` | HTTP **302** → `/login` ✅ |

### 遇到的问题（如实记录）

| # | 问题 | 根因 | 处置 |
|---|---|---|---|
| 1 | 写 `login.html` 时报 `ReferenceError: systemName is not defined` | Thymeleaf 的 ``{systemName}` 写在 JS **模板字符串**里，被当成变量插值了 | 用 ``$`` 占位再替换成 `\$` |
| 2 | 首次验证时启动日志出现 `Failed to start bean 'webServerStartStop'` | **端口 8080 上已有旧实例**（spring-boot-devtools 检测到 `target/classes` 变化后**热重启**，把 T-005 的代码加载进去了，所以我测到的确实是新代码） | 改为在**独立端口 8081** 起一个干净实例重新验证 |
| 3 | 我无法结束 8080 上那个进程 | DSH 沙箱看不到也杀不掉它 | ⚠️ **留给项目负责人确认**（很可能是 IDEA 里跑着的那个） |

### 遗留与下一步

- ⏸️ **现在输任何账号密码都登不进去**（没有 `UserDetailsService`）→ **T-006**
- ⏸️ 登录成功后跳哪：目前是"回到你原本想访问的页面"（Spring 默认）→ T-007 再看是否按角色跳
- ➡️ 下一步：**T-006** 三种登录方式（学号 / 用户名 / 手机号 + 密码）
