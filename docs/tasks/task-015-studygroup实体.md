# task-015 · `StudyGroup` 实体 + 仓库

| 项 | 值 |
|---|---|
| 任务编号 | **T-015** |
| 任务名 | `StudyGroup` 实体 + `StudyGroupRepository` |
| 对应 | **M3 分组管理**（第一个任务） |
| 状态 | ✅ **已完成**（2026-10-03） |

---

## 1. 目标

把**已经建好**的 `study_groups` 表映射成 Java 实体 + 建仓库 —— 给 M3 分组管理打地基。

**表里已有 2 行种子数据**（真实查询结果）：

| id | name | description | leader_user_id | sort_order |
|---|---|---|---|---|
| 1 | **Python 组** | 以 Python 为主要学习语言的小组 | NULL | 1 |
| 2 | **C 语言组** | 以 C / C++ 为主要学习语言的小组 | NULL | 2 |

---

## 2. 非目标

- ❌ 不做分组管理页（**T-016**）
- ❌ 不做「指定组长」（**T-016**）
- ❌ 不做按组隔离（**T-017**，全项目最重要的一条权限规则）
- ❌ **不改 `users` 表**（`User.groupId` 继续是裸 ID）
- ❌ **不改数据库结构**（表早就建好了，本任务一行 SQL 都没执行）
- ❌ 不给任何组设组长（种子数据里就是 NULL）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `src/main/java/com/fountainhub/entity/StudyGroup.java` | **新建**（2397 字节） |
| `src/main/java/com/fountainhub/repository/StudyGroupRepository.java` | **新建** |
| 任务书 + plan.md + docs/09 | 更新 |

> ✅ **没有碰任何已有代码**

---

## 4. 关键设计（三个决定）

### 决定 1：主键用 `@GeneratedValue(IDENTITY)`

**和 `StudentProfile` 不同** —— 那个的主键**就是**外键（`user_id`），跟着人走，不能自增；
这张表的 `id` 是数据库自己造的（`AUTO_INCREMENT=3`），所以要 `@GeneratedValue`。

### 决定 2：`leader` 用 `@ManyToOne`（**而且是 EAGER**）

`java
@ManyToOne                       // ⚠️ 故意不写 LAZY
@JoinColumn(name = "leader_user_id")
private User leader;
`

**为什么用关联而不是裸 `Long leaderUserId`**：T-016 的组列表要显示「组长：张三」，用裸 ID 还得再查一次 users 表。

**⚠️ 为什么必须是 EAGER** —— **这是重构反过来影响设计的典型案例**：

> **R-012 刚把 Open-Session-In-View 关掉了**。一旦这里用 LAZY，
> 在 Thymeleaf 模板里访问 `group.leader.realName` 会直接抛
> `LazyInitializationException`（渲染页面时 Hibernate 会话已经关了）。
> 分组表只有个位数行，EAGER 完全没负担。

### 决定 3：字段对照表

| 数据库列 | Java 属性 | 备注 |
|---|---|---|
| `id` | `id` | `@GeneratedValue(IDENTITY)` |
| `name` | `name` | UNIQUE，`length = 50` |
| `description` | `description` | `length = 255` |
| `leader_user_id` | `leader` | `@JoinColumn`，**可为 null** |
| `sort_order` | `sortOrder` | 驼峰 ↔ 下划线 |
| `created_at` / `updated_at` | 同名字段 | `insertable=false, updatable=false`（数据库自己填） |

**仓库里加了一个派生查询**：`findAllByOrderBySortOrderAsc()` —— 把「按 sort_order 升序显示」这个设计意图写下来，T-016 直接用。

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | 列名/类型对不上 → 启动直接失败 | `ddl-auto: validate` 是自动测试；**并做了负向测试**证明它真在检查 ✅ |
| 2 | `name` 有唯一约束 | 实体加 `length = 50` 对齐；T-016 保存时要查重 |
| 3 | `leader_user_id` 现在是 NULL | `@ManyToOne` 默认可空 ✅ 正好合适 |
| 4 | ⚠️ 万一用 LAZY 会炸 | 已选 EAGER（见决定 2） |
| 5 | 不确定点 | 组列表排序 → 定为 `sort_order` 升序（数据库设计意图）✅ |

---

## 6. 验证命令

```cmd
cd /d D:\Project\FountainHub
mvn -B -ntp compile
mvn -o -B -ntp spring-boot:run
```

> ⚠️ **必须加 `-o`（离线模式）**：本机 DSH 沙箱不允许 Maven 写 `C:\Users\<你的用户名>\.m2`，
> 而 R-002 删掉依赖后 Maven 需要重新解析（就要写 .m2）→ 不加 `-o` 会报
> `AccessDeniedException: ...\resolver-status.properties` 而启动不了。
> 详见 [docs/09](../09-开发进度日志.md) 的 T-015 条目。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-03

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `entity/StudyGroup.java` | 新建 | 2397 字节 |
| `repository/StudyGroupRepository.java` | 新建 | — |

### 验证结果（真实输出）

`mvn compile` → ✅ `BUILD SUCCESS`

| 验证 | 真实结果 |
|---|---|
| **验证 1：正常启动** | ✅ `Started FountainHubApplication`；`Schema-validation` **无报错** → **映射与表结构逐列对得上**；`/ping` → `pong` |
| **验证 2：负向测试** | 故意把 `sort_order` 写成 `sort_order_typo` → ✅ **启动失败**，出现 `Schema-validation` 报错 → **证明 validate 真的在检查这个实体** |
| **验证 3：还原后** | ✅ 启动成功、无报错；**还原的文件与原来逐字节一致** ✅ |
| 沙箱干扰 | 验证 2 第一次做时被 `AccessDenied` 污染（"失败"是因为沙箱），**已重做并确认无沙箱干扰** ✅ |

### 遇到的问题（如实记录）

| # | 问题 | 根因 | 处置 |
|---|---|---|---|
| 1 | `mvn spring-boot:run` 启动失败（`AccessDeniedException: .m2\...\resolver-status.properties`） | R-002 删了依赖 → Maven 需要重新解析 → 要写 `.m2` → **沙箱不允许** | **加 `-o`（离线）就绕过了** ✅ 已记进 DEVLOG |
| 2 | **负向测试第一次的结论是无效的** | 它"启动失败"是因为沙箱 `AccessDenied`，**不是** `Schema-validation` | 用 `-o` **重做了一遍**，并加了一条判据「沙箱 AccessDenied 必须为 0」才承认结论 ✅ |
| 3 | 备选方案在工作区复制了 120 MB 的 `.m2repo` | 当时的探索 | 已删除 ✅ |

**⚠️ 验证的边界（如实说明）**：本任务**没有页面**，所以**无法端到端验证"真的读出了那 2 行数据"**。
能验证的是「**映射和表结构完全对得上**」（这正是本任务的主要风险）。
真正第一次用到它是在 **T-016** ✅

---

## 8. 遗留与下一步

- ⚠️ 顺手清理了**测试残留** `r008test（拆分测试）` —— 那是 R-008 验证时我调 `/reject` 没删掉的
  （reject 只删 `PENDING`，而它当时已经是 `DISABLED`）
- ➡️ 下一步：**T-016** 分组管理页 `/admin/groups`（组列表、新建组、**指定组长**）——
  ⭐ 顺便补上 T-010 欠下的「**学员列表按组筛选**」和「**组**」这一列
