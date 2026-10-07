# T-032 · 获奖管理（M8）

> 任务书 · 建立于 **2026-10-04 16:09**（批准后开始实现）
> 对应 [plan.md](../plan.md) T-032 · 需求 [01-需求规格说明书](../01-需求规格说明书.md) FR-8.1~8.6 · 验收 [spec.md](../spec.md) §4.2「T7 获奖」
> 表结构 [sql/01-schema.sql](../../sql/01-schema.sql) 第 11~13 张表（已建好，**不改表**）· 设计契约 [08-视觉与品牌规范](../08-视觉与品牌规范.md
> 规则：AGENTS.md §5（未经项目负责人确认，不开始实现）→ ✅ **2026-10-04 已批准**

---

## 1. 目标

把 `awards` / `award_students` / `award_images` 三张表**从空表变成可用功能**：

| # | 目标 | 依据 |
|---|---|---|
| 1 | 后台能**录入 / 编辑 / 删除**获奖：比赛名称、举办方、比赛时间、级别、奖项等级、指导老师 | FR-8.1~8.3 |
| 2 | ⭐ **团队奖**：一条记录可关联**多个学员** | FR-8.4 |
| 3 | ⭐ **证书照片可多张**：上传、显示、移除（复用 `POST /admin/upload`） | FR-8.5 |
| 4 | 🔒 **公开意愿由学员本人控制**：录入时**一律 `is_public = 0`**；学员在「我的获奖」里自己打开/撤回 | FR-8.6 + **红线**「获奖对外展示必须经本人同意」 |
| 5 | 总览页「获奖记录」格子：写死占位 → **真实条数** | FR-11.1（顺手，否则立刻过时） |

### 📌 批准时定下的三件事（2026-10-04）

| # | 决定 | 影响 |
|---|---|---|
| 1 | **没有设计稿** → 直接按 [08-视觉与品牌规范](../08-视觉与品牌规范.md 的 token / 组件 / 空状态规范实现 | 视觉需需求方目视确认 |
| 2 | **公开意愿只能由学员本人打开**（管理员**不能**代勾） | 后台表单只显示状态、**不提供勾选框**；学员端 `POST /me/awards/{id}/public` |
| 3 | **组长 ❌ 完全不能碰获奖**（与 T-017 一致） | 同步把 [01-需求规格说明书](../01-需求规格说明书.md) §5 权限矩阵里「管理获奖：组长 = 本组」改成 ❌ |

---

## 2. 非目标（明确不做）

- ❌ **T-033**：对外展示墙 `/awards`、公开页按级别/年份筛选、首页获奖板块、学员主页的获奖**展示**（本次只做「我的获奖」+ 开关）
- ❌ FR-8.9「按级别/年份统计获奖数量」（属统计，随 T-036）
- ❌ 导出 Excel（T-036）、获奖积分排名（需求明确不做）
- ❌ **不改三张表的表结构**
- ❌ 不做分页（获奖是几十条量级）
- ❌ 不做证书拖拽排序（按上传先后 + 保存时的顺序）
- ❌ **不做「单张证书立即删除」的独立端点** —— 改为**在编辑页点 × 移除、保存时生效**（少一个端点、行为更可预期；见 §5 风险 6）
- ❌ 不新增依赖、不引 CDN、不改架构、不改 `SecurityConfig`、不改 `UploadController`

---

## 3. 计划修改的文件

**新建**

| # | 文件 | 作用 |
|---|---|---|
| 1 | `docs/tasks/task-032-获奖管理.md` | 本文件 |
| 2 | `entity/AwardLevel.java`、`entity/Prize.java` | 两个枚举（`SCHOOL…INTERNATIONAL` / `FIRST…PARTICIPATION`），带中文 label |
| 3 | `entity/Award.java` | 映射 `awards` |
| 4 | `entity/AwardStudent.java` | 映射 `award_students`（含 `is_public`） |
| 5 | `entity/AwardImage.java` | 映射 `award_images` |
| 6 | `repository/AwardRepository.java` | 列表 / 排序 / 计数 |
| 7 | `repository/AwardStudentRepository.java` | 按奖查人、按人查奖、去重判断 |
| 8 | `repository/AwardImageRepository.java` | 按奖查图 |
| 9 | `controller/AdminAwardController.java` | 后台：列表（级别/年份筛选）/ 新建 / 编辑 / 删除 |
| 10 | `controller/MyAwardController.java` | 学员端：`/me/awards` + 公开开关 |
| 11 | `templates/admin/awards.html` | 后台列表 |
| 12 | `templates/admin/award-form.html` | 新建/编辑表单（学员多选 + 证书多图） |
| 13 | `templates/me/awards.html` | 「我的获奖」+ 开关 |

**修改**

| # | 文件 | 改动 |
|---|---|---|
| 14 | `templates/fragments/layout.html` | ⚠️ 后台子导航 **6 项 → 7 项**（加「获奖管理」，放在「新闻管理」之后）—— **已获批准** |
| 15 | `templates/admin/index.html` | 「获奖记录」格子 → 真实条数 + 链接（去掉「待排期」字样） |
| 16 | `controller/AdminHomeController.java` | 提供 `awardCount` |
| 17 | `templates/me/index.html` | 「常用功能」加一项「我的获奖」 |
| 18 | `docs/01` / `docs/08` / `docs/plan.md` / `README.md` / `docs/09` | 权限矩阵（组长 ❌）、页面清单、进度、DEVLOG |

**不改**：`SecurityConfig`、`UploadController`、`static/js/upload.js`、`sql/**`、`pom.xml`。

---

## 4. 实现步骤

1. 任务书（本文件）
2. 枚举 + 3 实体 + 3 仓库 → `mvn -o -B -ntp compile` → 启动验 **`ddl-auto: validate` 逐列吻合**（本项目加实体的固定动作）
3. `AdminAwardController`：列表（筛选）→ 新建 → 编辑（学员增删、证书增删）→ 删除
4. `MyAwardController`：`/me/awards` + `POST /me/awards/{id}/public`（**服务端校验这行是不是他自己的**）
5. 三个模板；总览格子；我的页入口；后台子导航 7 项
6. 8081 起实例 → E2E（含权限/越权/红线断言）→ **清理测试数据** → 杀进程
7. 回填 §7，更新 5 份文档

### 关键实现约定（写代码时不许改）

| # | 约定 | 为什么 |
|---|---|---|
| 1 | 新建时 `is_public` **一律 false**，后台**不接收**该字段 | 🔒 红线：管理员不得代学员表态 |
| 2 | `/me/awards/{id}/public` **必须校验** `award_students.user_id == 当前登录用户` | 否则能改别人的公开意愿 → 踩红线 |
| 3 | 编辑保存时，成员用**差量同步**（保留已勾选的、新增勾选的、删除未勾选的） | 若"清空重建"会把学员已打开的公开意愿**抹掉** |
| 4 | 证书图用**清空重建**（按提交顺序写入 `sort_order`） | 图片没有意愿字段，重建最简单且顺序可控 |
| 5 | `imagePaths` 每个值**必须以 `/uploads/` 开头**，否则丢弃 | 🔒 防把 `javascript:`/外链塞进 `<img src>` |
| 6 | 所有 `@RequestParam` 的枚举/日期/数字都走 `FormUtils`（非法 → 人话提示） | 不许出现 500 |
| 7 | 删除接口 `@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")` | 权限矩阵：**副社长不能删数据** |
| 8 | 页面**不得出现手机号**（成员只显示姓名 + 角色） | 🔒 全站红线 |

---

## 5. 风险与不确定点

| # | 风险 | 处理 |
|---|---|---|
| 1 | ⚠️ 后台子导航 6→7 项（改"定型"结构） | 已获批准；只加一项，不动顺序 |
| 2 | 删除获奖 → `award_students`/`award_images` **CASCADE 连带删** | 页面**二次确认**；⚠️ 磁盘上的证书文件**不删**（与新闻图片现状一致） |
| 3 | 编辑时移除某学员 → 他的公开意愿一并丢失 | 表单上**明确提示**（"移除后该学员的公开意愿会重置"） |
| 4 | 🔒 越权改别人意愿 | 服务端强制校验归属（约定 2），不靠页面隐藏 |
| 5 | `UNIQUE(award_id,user_id)`：重复加同一人 → 异常 500 | 落库前**去重**（`LinkedHashSet`） |
| 6 | 单张证书删除改为"保存时生效" | 页面提示"保存后生效"；这是**相对计划的一处简化**，已记录 |
| 7 | 验证会往库里写测试数据 | 结束时**用页面删除接口清理**，以"三张表回到 0 行"为证据 |
| 8 | 没有设计稿 | 严格用现有 `fh-*` 类与 token；样式需需求方目视确认 |
| 9 | `awards.award_date` 可为空 | 排序与筛选要容忍 null（按日期倒序时 null 排最后） |

---

## 6. 验证命令

```powershell
cd D:\Project\FountainHub
mvn -o -B -ntp compile
mvn -o -B -ntp spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

**自动断言**（PowerShell + Cookie，贴真实输出）：见 §7 的表。

**需要项目负责人目视确认**：`/admin/awards`、`/admin/awards/new`、`/me/awards` 三个页面，以及后台子导航是否变成 **7 项**。

---

## 7. 完成记录

> 完成后回填**真实结果**（AGENTS.md §10 不许编）。

**状态**：✅ **已完成**（2026-10-04 16:16）

**验证结果（真实输出，独立实例 8081；证据重跑一次，逐条列出）**

```
PASS  ① Schema-validation 通过（应用能起来）
PASS  ② 老师 GET /admin/awards -> 200
PASS  ② 新建页 -> 200 且取到 CSRF 头名
PASS  ⑤ 证书上传 -> ok:true（/uploads/2026/10/856e8c3a…png）
PASS  ③ 新建团队奖（1 奖 3 人）-> 302
PASS  ③ awards=1（1）
PASS  ③ award_students=3（3）
PASS  ⑤ award_images=1（1）
PASS  ④ is_public 合计=0（0 = 默认不同意）
PASS     编辑页含证书图与 3 个勾选
PASS  🔒 编辑页没有公开意愿勾选框
PASS  🔒 编辑页无手机号
PASS  ⑥ 重复提交同一人 -> 302 且去重为 1（1）
PASS  ⑦ 学员 GET /admin/awards -> 403
PASS  ⑨ 学员 GET /me/awards -> 200 且含比赛名
PASS  🔒 我的获奖页无手机号
PASS  ⑩ 学员打开自己的公开 -> 302
PASS  ⑩ 库中 is_public=1（1）
PASS  ⑪ 学员改【别人】的意愿 -> 403
PASS  ⑪ 别人那行没变，仍是 0（0）
PASS     撤回公开 -> is_public=0
PASS     副社长 GET /admin/awards -> 200
PASS     副社长页面【没有】删除按钮（第 1 道锁）
PASS  ⑧ 副社长 POST 删除 -> 403（第 2 道锁）
PASS  ⑧ 数据还在，awards=1（1）
PASS     角色已还原 = STUDENT（STUDENT）
PASS  ⑬ 游客 / /about /news /ping -> 200
PASS  ⑬ 登录 /admin /admin/news /me /me/awards /me/attendance /me/study -> 200
PASS     总览不再显示「获奖功能待排期」
PASS  🔒 后台获奖列表无手机号
PASS  ⑭ 老师删除 -> 302
PASS  ⑭ 清理后 awards=0 / students=0 / images=0（全 0）

===== 证据重跑：40 通过 / 0 失败 =====
（连同之前两批 18 + 29 条，本次共 87 条断言、0 失败）
```

| 项 | 结果 |
|---|---|
| 编译 | `mvn -o -B -ntp compile` → **BUILD SUCCESS** |
| 8081 启动 | Tomcat started on port 8081；`Found 14 JPA repository interfaces`（新增 3 个）；**Schema-validation 无报错**（三张表逐列吻合） |
| 日志 | **ERROR 0 条、Exception 0 条**；仅启动期 1 条 `LiveReload` WARN（历史既有） |
| 测试数据 | ✅ **已清理**：三张表回到 0 行（顺带验证了 CASCADE 删除） |
| 遗留文件 | ⚠️ 磁盘留 **2 张 70 字节测试图**（`uploads/` 已被 .gitignore 忽略，不进版本库）；`upload_files` 表 28 → 30 行 |
| 8081 收尾 | ✅ 已释放、无残留 java 进程；需求方 8080 实测 `/ping` → 200 pong |
| 提交 | 未提交（等需求方确认） |
| 完成日期 | **2026-10-04 16:16** |

**⚠️ 一处相对计划的简化（已记录）**：计划里写「单张证书立即删除」的独立端点，
实际实现为**在编辑页点 × 移除、保存时生效**（少一个端点、行为更可预期、也不会出现"删了图但记录还在"的中间态）。

**⚠️ 我自己脚本里的一个瑕疵**（不隐瞒）：有一行断言的**文字标签先算、条件后算**，
所以打印出来是「撤回公开 -> is_public=1（0）」看起来矛盾 —— 那个 1 是**动作前**的旧值，
条件本身是动作后重新查询的（结果 0），断言是**真通过**的。教训：标签和断言要在同一个时点取值。
