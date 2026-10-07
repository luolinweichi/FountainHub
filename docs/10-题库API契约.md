# 题库 API 契约（T-031 · P1 只写契约，P2 按契约实现）

> **建立于 2026-10-04**（T-031）
> **上位依据**：[03-一期P1实现清单](03-一期P1实现清单.md) §2.1「题库：P1 不做界面，但必须预留接口」
> **需求依据**：[01-需求规格说明书](01-需求规格说明书.md) FR-4.x（在线选择题）/ FR-5.x（外链编程题）/ FR-6.7（热力图数据来源）
> **表结构依据**：[sql/01-schema.sql](../sql/01-schema.sql) 第 5~9 张表（**已建好，本契约不得改动它们**）
> **设计契约**：[08-视觉与品牌规范](08-视觉与品牌规范.md)

---

## 1. 目的与范围

| 项 | 说明 |
|---|---|
| **为什么现在写** | 题库是**二期**功能，一期不做界面；但**接口与数据结构必须先定死**，否则二期会牵动一次伤筋动骨的改造（P1 清单 §2.1 原话） |
| **本契约是什么** | 一份**能照着实现**的接口文档：路径 / 方法 / 参数 / 权限 / 校验 / 返回 / **副作用（写哪张表）** |
| **形态（项目负责人 2026-10-04 拍板）** | ⭐ **服务端渲染路由 + 表单契约**（与现有签到、新闻、分组**完全同一种写法**）——零 JS 框架、零 CDN、判分在服务端 |
| **不写什么** | ❌ 自建 OJ 判题 ❌ 代码沙箱 ❌ 抓取洛谷/力扣/牛客的数据（**编程题只做跳转**） |
| **承诺** | P2 按本契约实现后：**表结构不改、`/me/study` 页面骨架不改、热力图（`/me`）一行代码都不用改** |
| **谁遵守** | 二期实现者；若要偏离契约，**先改本文档**（见 §10） |

---

## 2. 现状事实（写代码前必须知道）

### 2.1 五张表已经建好（`sql/01-schema.sql` L115-200）

| 表 | 作用 | 现状 |
|---|---|---|
| `chapters` | 章节（题库的组织单位） | 已建，空表 |
| `questions` | 选择题 | 已建，空表 |
| `quiz_answers` | 答题记录 | 已建，空表 |
| `oj_problems` | 外链编程题 | 已建，空表 |
| `oj_completions` | 外链题完成标记 | 已建，空表 |

### 2.2 逐列字段表（**与 `sql/01` 一字不差**；实现时以脚本对齐为准）

**`chapters`（章节）**

| 列 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | 主键 |
| `title` | VARCHAR(100) | 否 | 章节标题 |
| `language` | VARCHAR(20) | **是** | 方向 `PYTHON` / `C` |
| `description` | VARCHAR(500) | **是** | 章节说明 |
| `sort_order` | INT | 否 | 排序（默认 0） |
| `created_at` / `updated_at` | DATETIME | 否 | 时间戳 |

**`questions`（选择题）**

| 列 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | 主键 |
| `chapter_id` | BIGINT UNSIGNED | 否 | 外键 → `chapters.id`（**ON DELETE CASCADE**） |
| `type` | VARCHAR(20) | 否 | `SINGLE` 单选 / `MULTIPLE` 多选 / `JUDGE` 判断 |
| `stem` | TEXT | 否 | 题干 |
| `options` | JSON | **是** | 选项，如 `["A. xx","B. yy"]` |
| `answer` | JSON | 否 | 正确答案，如 `["A"]` 或 `["A","C"]` |
| `analysis` | TEXT | **是** | 答案解析 |
| `image_path` | VARCHAR(255) | **是** | 题干配图 |
| `code_block` | TEXT | **是** | 代码片段 |
| `sort_order` | INT | 否 | 排序 |

**`quiz_answers`（答题记录）**

| 列 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | 外键 → `users.id` |
| `question_id` | BIGINT UNSIGNED | 否 | 外键 → `questions.id` |
| `submitted` | JSON | 否 | 学员提交的答案 |
| `is_correct` | TINYINT(1) | 否 | 是否正确 |
| `answered_at` | DATETIME | 否 | 作答时间 |

> ⚠️ **`quiz_answers` 没有唯一约束** → 同一题可以**多次作答**，每次**追加一行**（这是"允许重做"的设计，见 §7 防刷规则）。

**`oj_problems`（外链编程题）**

| 列 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | 主键 |
| `title` | VARCHAR(200) | 否 | 题目标题 |
| `chapter_id` | BIGINT UNSIGNED | **是** | 外键 → `chapters.id`（**ON DELETE SET NULL**） |
| `difficulty` | VARCHAR(20) | **是** | `EASY` / `MEDIUM` / `HARD` |
| `platform` | VARCHAR(20) | 否 | `LUOGU` / `LEETCODE` / `NOWCODER` / `OTHER` |
| `url` | VARCHAR(500) | 否 | 外部题目链接 |
| `sort_order` | INT | 否 | 排序 |

**`oj_completions`（外链题完成标记）**

| 列 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | 外键 → `users.id` |
| `problem_id` | BIGINT UNSIGNED | 否 | 外键 → `oj_problems.id` |
| `status` | VARCHAR(20) | 否 | `PENDING` 待确认 / `CONFIRMED` 已确认 / `REJECTED` 已驳回（默认 `PENDING`） |
| `confirmed_by` | BIGINT UNSIGNED | **是** | 确认人（外键语义 → `users.id`，**表上未声明外键**） |
| `created_at` | DATETIME | 否 | 时间戳 |

> 🔑 **唯一键 `uk_oj_completion (user_id, problem_id)`** → 一个人对一道外链题**只有一条**记录，重复提交要更新而不是新增。

### 2.3 已经存在、本契约要接上的东西

| 东西 | 位置 | 契约怎么接 |
|---|---|---|
| 热力图读 `checkins`，**按日期求和** | `MyHomeController.java` L103-107（`byDate.merge(d, v, Integer::sum)`） | 见 §3 |
| 指标枚举 `PROBLEM_COUNT`（题数） | `CheckinMetric.java` | 题库写数据时用它 |
| 来源枚举 `QUIZ` / `OJ` | `CheckinSource.java` | 选择题用 `QUIZ`，外链题用 `OJ` |
| 学习页占位：`GET /me/study` | `MyHomeController.java` L234 | **P2 原地升级**为章节列表，**路径不变** |
| 目前**没有任何题库 Java 代码** | 实测 grep 0 命中 | 全部新建 |

---

## 3. ⭐ 与热力图的契约（**本契约最核心的一条**）

> **一句话**：**答对一道选择题 = 往 `checkins` 写一行**；因为热力图是**按日期求和**的，所以写进去就自动生效，**页面与表都不用改**。

**写入规则（选择题）**

| 项 | 值 |
|---|---|
| `user_id` | 当前登录学员 |
| `checkin_date` | **当天**（服务端取日期，**不接受前端传日期**） |
| `metric_type` | `PROBLEM_COUNT` |
| `metric_value` | **1**（一题一行） |
| `source` | `QUIZ` |
| `operator_id` | **NULL**（题库自动产生，没有人代录） |
| `note` | 可空（建议留空，不要写题干，避免冗余） |

**写入规则（外链题）**

| 项 | 值 |
|---|---|
| `source` | `OJ` |
| `metric_value` | **1** |
| 触发时机 | **社长/老师把 `oj_completions.status` 改成 `CONFIRMED` 时**（不是学员自己标记时） |
| 其余字段 | 同上（`metric_type` / `checkin_date` / `operator_id=NULL`） |

**为什么"每题一行"**（2026-10-04 拍板）：
热力图按日期**求和**，所以「每题一行、每行 1 分」与「一天一行、值为当天题数」**结果一样**；
但每题一行**可追溯**（能和 `quiz_answers` 一一对应），而且**不需要"先查再改"同一行**（避开并发覆盖）。

**验收方式**：P2 上线后，学员答对 3 题 → `/me` 的今日数字应为 **3**，热力图当天格子点亮（**不改任何热力图代码**）。

---

## 4. 学员端端点契约

> 前缀 `/me/study`。**权限**：登录用户（学员、组长、社长、老师都可用；**各自只看自己的数据**）。
> 共通：所有 `POST` 都必须带 CSRF —— **表单字段名 `_csrf`**（不是请求头名），漏了会 403。

### 4.1 `GET /me/study` —— 章节列表（P2 原地替换占位页）

| 项 | 内容 |
|---|---|
| 参数 | 可选 `lang`（`PYTHON` / `C`）用于筛选；非法值忽略 |
| 返回 | 视图 `me/study`（P2 重写模板）：章节卡片列表 + 每章题数 + **我的进度**（该章答对题数 / 总题数） |
| 副作用 | 只读 |
| 备注 | **路径与现在完全一致**，所以导航「学习」入口不用改 |

### 4.2 `GET /me/study/chapter/{chapterId}` —— 章节答题页

| 项 | 内容 |
|---|---|
| 参数 | 路径参数 `chapterId` |
| 返回 | 视图 `me/study-chapter`：该章节题目列表（`stem` / `type` / `options` / `image_path` / `code_block`）+ 一题一个作答表单 |
| 🔒 安全 | **绝不下发 `answer` 与 `analysis`**（防 F12 看答案） |
| 失败 | 章节不存在 → **404**；未登录 → 302 → `/login` |
| 副作用 | 只读 |

### 4.3 `POST /me/study/answer` —— 提交一题并判分 ⭐

| 项 | 内容 |
|---|---|
| 表单字段 | `questionId`（必填，数字）、`choice`（必填；**可重复出现**的多值字段，单选传 1 个）、`_csrf` |
| `choice` 取值 | 选项字母（`A` / `B` / `C` / `D` …），对应 `options` 数组下标 |
| 校验 | ① 题目存在 ② `choice` 非空且都是合法选项 ③ `type=MULTIPLE` 允许 1 个以上，`SINGLE`/`JUDGE` 只允许 1 个 |
| 判分 | **只在服务端**：把提交集合与 `questions.answer` 比较（**顺序无关**，集合相等即正确） |
| 成功返回 | **302 → `/me/study/chapter/{chapterId}?answered={questionId}`**（PRG 模式，防刷新重复提交）；目标页高亮该题并显示**正确答案 + 解析** |
| 失败返回 | **302 → 同页** `?error=…`（人话提示；非法参数**不报 500**，与现有页面同风格） |
| 副作用 ① | **追加**一行 `quiz_answers`（`user_id` / `question_id` / `submitted`=提交的 JSON 数组 / `is_correct` / `answered_at`） |
| 副作用 ② | 仅当**答对**、且**该题当天尚无答对记录**时，追加一行 `checkins`（按 §3） |
| 幂等/防刷 | 同一题可重做（每次追加 `quiz_answers`），但**同一天同一题只计一次分**（见 §7.4） |

### 4.4 `GET /me/study/oj` —— 外链编程题列表

| 项 | 内容 |
|---|---|
| 参数 | 可选 `chapterId` 筛选 |
| 返回 | 视图 `me/study-oj`：按章节分组，显示 `title` / `difficulty` / `platform`；右侧显示**我的状态**（未提交 / 待确认 / 已确认 / 已驳回） |
| 外链 | `<a href="${url}" target="_blank" rel="noopener noreferrer">` |
| 副作用 | 只读 |

### 4.5 `POST /me/study/oj/{problemId}/complete` —— 标记"我做完了"

| 项 | 内容 |
|---|---|
| 表单字段 | 无业务字段（路径参数足够）+ `_csrf` |
| 返回 | 302 → `/me/study/oj`（带提示） |
| 副作用 | 写 `oj_completions`，`status=PENDING`、`confirmed_by=NULL`；**已存在记录时**：`REJECTED` → 重置为 `PENDING` 重新申请，`PENDING`/`CONFIRMED` → 提示"已提交过"**不新增**（受唯一键 `uk_oj_completion` 约束） |
| 🔒 注意 | **此时不写 `checkins`**（要等社长确认）；也**不校验**学员是否真的在洛谷做完了（无法校验，靠诚信 + 社长抽查） |

---

## 5. 后台端点契约

> 前缀 `/admin/study`。**权限**：`TEACHER` / `PRESIDENT` / `VICE_PRESIDENT`；
> 🔒 **组长与学员一律 403**（与 `/admin/**` 现有规则一致；副社长可审核，但**不能删数据**，见 §6）。
> 共通：全部 `POST` 带 `_csrf`；所有写操作成功后用 **302 重定向回列表页**（PRG）。

| # | 路径 | 方法 | 作用 | 关键字段 |
|---|---|---|---|---|
| 5.1 | `/admin/study/chapters` | GET | 章节列表 | — |
| 5.2 | `/admin/study/chapters/new` | GET / POST | 新建章节 | `title`（必填）、`language`、`description`、`sort_order` |
| 5.3 | `/admin/study/chapters/{id}/edit` | GET / POST | 编辑章节 | 同上 |
| 5.4 | `/admin/study/chapters/{id}/delete` | POST | 删除章节 | ⚠️ 见下方"级联风险" |
| 5.5 | `/admin/study/questions` | GET | 题目列表（按章节筛选） | — |
| 5.6 | `/admin/study/questions/new` | GET / POST | 新建题目 | `chapter_id`、`type`、`stem`、`options`、`answer`、`analysis`、`image_path`、`code_block`、`sort_order` |
| 5.7 | `/admin/study/questions/{id}/edit` | GET / POST | 编辑题目 | 同上 |
| 5.8 | `/admin/study/questions/{id}/delete` | POST | 删除题目 | — |
| 5.9 | `/admin/study/oj` | GET | 外链题列表 | — |
| 5.10 | `/admin/study/oj/new`、`/admin/study/oj/{id}/edit` | GET / POST | 新建 / 编辑外链题 | `title`、`chapter_id`、`difficulty`、`platform`、`url`、`sort_order` |
| 5.11 | `/admin/study/oj/{id}/delete` | POST | 删除外链题 | — |
| 5.12 | `/admin/study/oj-completions` | GET | **待确认**列表（默认只列 `PENDING`，可切全部） | — |
| 5.13 | `/admin/study/oj-completions/{id}/confirm` | POST | 确认完成 | 写 `status=CONFIRMED` + `confirmed_by`=当前用户 → **此时写一行 `checkins`（`source=OJ`）** |
| 5.14 | `/admin/study/oj-completions/{id}/reject` | POST | 驳回 | 写 `status=REJECTED` + `confirmed_by`；**不写 `checkins`** |

### ⚠️ 两个必须写进实现的坑

1. **删除章节会连带删题目** —— `questions.chapter_id` 是 **ON DELETE CASCADE**。
   → 契约要求：**该章节下还有题目时，拒绝删除**并提示"请先清空该章节的题目"（防止一键删掉几十道题）。
2. **`options` / `answer` 是 JSON 列**。
   → 页面用**多行文本框**（一行一个选项 / 一行一个答案），服务端负责组装成 JSON 数组存库；
   **不要**把 JSON 原文直接丢给社长手写（容易写坏）。`answer` 为空或格式非法 → 拒绝保存并提示。

---

## 6. 权限矩阵

| 能力 | 指导老师 | 正社长 | 副社长 | 组长 | 学员 |
|---|---|---|---|---|---|
| 看章节 / 答题 / 看外链题 | ✅ | ✅ | ✅ | ✅ | ✅（**只看自己的**） |
| 标记外链题完成 | ✅ | ✅ | ✅ | ✅ | ✅（仅自己） |
| 录入章节 / 题目 / 外链题 | ✅ | ✅ | ✅ | ❌ 403 | ❌ 403 |
| 确认 / 驳回外链题完成 | ✅ | ✅ | ✅ | ❌ 403 | ❌ 403 |
| 删除章节 / 题目 | ✅ | ✅ | ❌ 403 | ❌ 403 | ❌ 403 |

> 与 `docs/01` 权限矩阵一致：**副社长不删数据**；**组长对 `/admin/**` 一律 403**。

---

## 7. 🔒 红线与安全约束

| # | 红线 | 为什么 |
|---|---|---|
| 7.1 | **判分只在服务端**；`answer` / `analysis` **只在提交后**才出现在返回 HTML 里 | 否则 F12 一看就作弊；这是"自动判分"能成立的前提 |
| 7.2 | **编程题只做外链跳转**，不自建判题、不抓取第三方 OJ 数据、不调用任何外部接口 | 自建 OJ 难度极高（需求方明确否定）；**且"数据出得去"类操作需先获批**（AGENTS.md §7） |
| 7.3 | 外链 `url` 必须校验：**必须 `https://` 开头**，且 host 属于**白名单**（`www.luogu.com.cn` / `leetcode.cn` / `www.nowcoder.com`），其余落 `OTHER` 也要 `https` | 防 `javascript:` / `data:` 伪协议 → 点击即 XSS |
| 7.4 | **同一天同一题只计一次分**（`checkins` 防刷）：写 `checkins` 前先查当天是否已有该题答对记录 | 否则刷同一题就能把热力图刷爆 |
| 7.5 | 🔒 **任何页面都不得展示学员手机号** | 全站红线；题库页涉及"谁答对了多少"，**只显示姓名** |
| 7.6 | 删除章节**有题则拒绝**（见 §5 坑 1） | 保护数据，避免 CASCADE 连带删除 |
| 7.7 | **不引入新依赖**、**不引 CDN** | 项目原则（T-024 的 Markdown 渲染器就是自写的） |
| 7.8 | 题干里的代码片段按**纯文本**渲染（`code_block` 转义后输出） | 防 XSS；与新闻正文同一套思路 |

---

## 8. 枚举字典（**必须与数据库注释一字不差**）

| 归属 | 枚举值 | 中文 |
|---|---|---|
| `chapters.language` | `PYTHON` / `C` | Python 方向 / C 语言方向 |
| `questions.type` | `SINGLE` / `MULTIPLE` / `JUDGE` | 单选 / 多选 / 判断 |
| `oj_problems.platform` | `LUOGU` / `LEETCODE` / `NOWCODER` / `OTHER` | 洛谷 / 力扣 / 牛客 / 其它 |
| `oj_problems.difficulty` | `EASY` / `MEDIUM` / `HARD` | 简单 / 中等 / 困难 |
| `oj_completions.status` | `PENDING` / `CONFIRMED` / `REJECTED` | 待确认 / 已确认 / 已驳回 |
| `checkins.metric_type` | `PROBLEM_COUNT`（题库用）/ `STUDY_MINUTES` / `CHAPTER_COUNT` | 题数 / 学习时长 / 章节数 |
| `checkins.source` | `QUIZ`（选择题）/ `OJ`（外链题）/ `MANUAL` / `ADMIN` | 题库 / 外链 OJ / 自助 / 管理员 |

---

## 9. P2 落地建议（供以后排期，不是本任务范围）

| 顺序 | 建议任务 | 说明 |
|---|---|---|
| 1 | 实体 + 仓库（5 张表） | 照 §2.2 映射；启动时 `ddl-auto: validate` 必须通过 |
| 2 | 后台：章节 + 题目 CRUD | 先有数据才能答题；先做 3~5 道示例题 |
| 3 | 学员端：章节列表 + 答题 + 判分（§4.1~4.3） | **含 §3 的 `checkins` 写入**，接上热力图 |
| 4 | 后台：外链题 CRUD（§5.9~5.11） | |
| 5 | 学员端：外链题列表 + 标记完成（§4.4~4.5） | |
| 6 | 后台：完成确认（§5.12~5.14，含 `source=OJ` 写入） | |
| 7 | （可选）我的答题记录 / 错题本 | 需求里属"二期可选"，**本契约不预留端点**，避免过度设计 |
| 8 | 把 `/me/study` 占位页替换为真页面 | 路径不变，导航不用动 |

---

## 10. 契约变更流程

1. **契约先行**：先改本文档，再改代码（与全项目 SDD 一致）；
2. 改完必须在 [09-开发进度日志](09-开发进度日志.md) 记一条，并同步 [plan.md](plan.md) 与 [CODE_MAP.md](CODE_MAP.md)；
3. 表结构**原则上不动**；万一必须动，属「改数据库结构」→ **先经项目负责人批准**（AGENTS.md §7）。

---

## 11. 本任务的验收标准（T-031 自身）

| # | 标准 | 判定 |
|---|---|---|
| 1 | 文档存在且覆盖：学员端 5 个端点 + 后台 14 个端点 | 逐条对照 §4 / §5 |
| 2 | 文档里出现的**每个列名**都能在 `sql/01-schema.sql` 找到 | 脚本比对，**差异 0** |
| 3 | 契约里的**每个路径**与现有路由**不冲突** | 脚本比对，**冲突 0** |
| 4 | §3 热力图联动与现有聚合口径（**按日期求和**）一致 | 代码行号已引证（`MyHomeController` L103-107） |
| 5 | 文档内相对链接全部可达 | 脚本检查 |
| 6 | 无手机号 / 密码 / 密钥痕迹 | 脚本检查 |
