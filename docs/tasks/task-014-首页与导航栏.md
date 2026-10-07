# task-014 · 首页 + 全站导航栏（收尾）

| 项 | 值 |
|---|---|
| 任务编号 | **T-014**（原「长期评语」已取消，编号重新用于收尾） |
| 任务名 | **首页 + 全站导航栏**（含公共 layout fragment） |
| 页面 | `/` + `templates/fragments/layout.html` |
| 对应 | **收尾**（[CODE_MAP](../CODE_MAP.md) §4.1 列出的三大缺口） |
| 状态 | ✅ **已完成**（2026-10-02） |

---

## 1. 目标

把系统从「能跑」变成「**能点**」。解决 CODE_MAP 里排名前三的缺口：

| # | 原来的问题 | 现在 |
|---|---|---|
| 1 | `/` 还是里程碑 1 的占位页（文案都过时了） | ✅ **真正的首页**：社团介绍 + 登录/注册入口 + 建设进度 |
| 2 | **全站没有任何导航**，只能手输网址 | ✅ **顶部导航栏**，出现在 **9 个页面**上，按角色显示不同链接 |
| 3 | **登录后看不出自己登进去了** | ✅ 导航栏显示「**你好，真实姓名**」+ 退出按钮 |

### 导航栏的三种形态（只放已存在的页面，避免死链）

| 身份 | 导航栏 |
|---|---|
| 🌐 游客 | 首页 · 登录 · **注册**（蓝底按钮） |
| 🔑 已登录 | 首页 · 我的资料 · 修改密码 · **你好，XXX** · 退出登录 |
| 🔒 社长层 | 上面这些 **+ 学员列表** |

---

## 2. 非目标

- ❌ **新闻板块**（M9 还没做，等 **T-027** 接上）
- ❌ `/about` 独立页（**T-028**）
- ❌ 获奖展示墙（M8 待商定）
- ❌ **不改任何后端业务逻辑**，不改安全规则

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `templates/fragments/layout.html` | **新建**（公共外壳 + 导航栏） |
| `templates/index.html` | **重写**成真正的首页 |
| 其余 7 个模板 | 改：head 换 fragment、插入导航栏、样式类改用公共名 |
| `config/GlobalModelAdvice.java` | **新建**（全站公共模型数据） |
| 任务书 + plan / docs09 / README / CODE_MAP | 更新 |

---

## 4. 关键设计

### 4.1 为什么要有 `fragments/layout.html`

导航栏要在 **9 个页面**上都出现。如果每个页面复制一份，就是**同一段 HTML 写 9 遍**（CODE_MAP 的 **D3/D4** 问题）—— 改一个链接要改 9 个地方。

现在每个页面只要两行：

`html
<head th:replace="~{fragments/layout :: head('登录')}"></head>
<nav  th:replace="~{fragments/layout :: nav}"></nav>
`

> ✅ 顺带把 REFACTOR_PLAN 的 **R-05（抽公共 layout）** 和 **R-11（常量集中）** 一起做掉了。

### 4.2 `GlobalModelAdvice`：一处提供全站公共数据

`java
@ControllerAdvice
public class GlobalModelAdvice {
    @ModelAttribute
    public void addCommonAttributes(Model model, Authentication authentication) { ... }
}
`

**每个 Controller 方法执行前都会先跑它**，把 `systemName` / `clubName` / `navUserName` 塞进 Model。这样：
- 不用每个 Controller 手动塞（不会漏）
- 系统名/社团名只有一份（R-11）

### 4.3 ⚠️ 一个容易踩的坑：判断「有没有登录」不能用 `isAuthenticated()`

Spring Security 给**游客**也套了一个 `AnonymousAuthenticationToken`，它的 `isAuthenticated()` **返回 true**！
所以必须显式排除匿名身份：

`html
<!-- ❌ 错的：游客也会看到"你好，XXX" -->
<span sec:authorize="isAuthenticated()">

<!-- ✅ 对的 -->
<span sec:authorize="!isAnonymous()">
`

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | **9 个页面都要动**，容易漏 | 改完逐个页面实测 200 + 导航存在 ✅ |
| 2 | **导航栏出现死链** | 只放**已存在**的页面；新闻/获奖一律不放 ✅ |
| 3 | **退出登录必须 POST** | 写成 GET 会失败；用 `th:action` 表单（自动带 CSRF）✅ 实测 302 ✅ |
| 4 | ⚠️ 模板链接表达式写错 → **500** | **这次真的踩了**（见第 7 节）|
| 5 | 页面外观变化（外壳统一了） | 全站改成「深色导航 + 浅灰底 + 白卡片」，需要项目负责人**目视确认** |

---

## 6. 验证命令

`cmd
cd /d D:\Project\FountainHub
mvn -B -ntp compile
mvn -B -ntp spring-boot:run
`

然后浏览器打开 `http://localhost:8080/` 开始点。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `fragments/layout.html` | 新建 | 6527 字节 |
| `index.html` | 重写 | 4767 字节 |
| `GlobalModelAdvice.java` | 新建 | 2084 字节 |
| 其余 7 个模板 | 改造 | — |

### 验证结果（真实输出，独立实例 8081，日志零错误）

`mvn compile` → ✅ `BUILD SUCCESS`，源文件 17 → **18**

| 验证 | 真实结果 |
|---|---|
| 游客 `GET /` | **200**，标题「首页 · FountainHub」，社团介绍 ✅ 进度说明 ✅ |
| 游客的导航栏 | 登录 ✅ 注册 ✅ **没有「学员列表」** ✅ |
| `/login` `/register` `/403` | 全部 **200**，导航栏都在 ✅ |
| 老师 `GET /` | 200，问候「**指导老师**」✅ |
| 老师的导航栏 | 学员列表 ✅ 退出登录 ✅ 注册按钮**已隐藏** ✅ |
| 4 个后台/个人页 | `/admin/students` `/me/profile` `/me/password` `/admin/students/6` → 全部 **200** + 导航 ✅ |
| 学员 `GET /` | 200，问候「学员」✅，**没有「学员列表」** ✅ |
| 学员 `/admin/students` | **403** ✅（**导航藏起来 ≠ 放开权限**）|
| `POST /logout` | **302** → `/login?logout` ✅；退出后导航变回 登录/注册 ✅ |
| 死链检查 | 导航里 4 个链接全部 **200** ✅ |
| 运行日志错误 | **无** ✅ |

### 遇到的问题（如实记录）

| # | 问题 | 根因 | 处置 |
|---|---|---|---|
| 1 | **同一个错误犯了第 6、7 次**：`@{...}` 写成 `${...}`，layout.html 8 处 + index.html 8 处 | 写链接表达式的机械动作 | **自检脚本两次都当场抓到** ✅；但第一次我只扫了新写的 layout.html，**漏扫了 index.html** → 已经改成**每次扫全部 9 个模板** |
| 2 | `GET /` 一度返回 **500** | 就是上面 index.html 的 8 处 | 修复后 200 ✅ |
| 3 | **我的样式提取脚本切坏了 login.html** | 它有一段**注释里提到了 `<style>`**，正则跨过注释把文件切坏 | 修好尾巴 + **加了"每个文件 div 开合是否平衡"的检查** ✅ |

---

## 8. 遗留与下一步

- ⏸️ **新闻板块** → **T-027**（M9 做完后接到首页上）
- ⏸️ `/about` → **T-028**
- ➡️ 下一步：**T-015** `StudyGroup` 实体 + 仓库（**M3 分组管理**）
