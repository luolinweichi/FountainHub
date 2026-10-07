# task-009 · StudentProfile 实体 + 仓库

| 项 | 值 |
|---|---|
| 任务编号 | **T-009** |
| 任务名 | `StudentProfile` 实体 + 仓库（与 `users` 一对一） |
| 对应 | **M2 阶段 2 学员档案**（第一个任务） |
| 状态 | ✅ **已完成**（2026-10-02） |

---

## 1. 目标

把 `student_profiles` 表映射成 Java 实体，并建仓库接口 —— **M2 学员档案的地基**。

### 表结构（来自 sql/01-schema.sql）

| 列 | 类型 | Java 类型 |
|---|---|---|
| `user_id` | BIGINT **PK + FK→users.id** | `Long` |
| `grade` | VARCHAR(20) | `String` |
| `major_class` | VARCHAR(50) | `String` |
| `qq` / `wechat` / `email` | | `String` |
| `join_date` | **DATE** | ⚠️ `LocalDate`（**不是** LocalDateTime） |
| `languages` | VARCHAR(100) | `String` |
| `level` | VARCHAR(20) | 枚举 `Level` |
| `created_at` / `updated_at` | DATETIME | 数据库自动填 |

### 三个关键设计决定

| # | 决定 | 为什么 |
|---|---|---|
| 1 | 用「裸 `Long userId`」当主键，**不用** `@OneToOne` | 与 T-001 的 `User.groupId` 同一风格（先把最直白的映射做稳）；T-010 列表要的字段全在 users 表，暂时不需要 join |
| 2 | `join_date` 用 `LocalDate` | 数据库是 DATE（无时分秒），用 LocalDateTime 语义不对 |
| 3 | `level` 用枚举 `Level` | 与 Role / UserStatus 一致，打错字编译就报错 |

---

## 2. 非目标

- ❌ 不写页面 / Service / Controller
- ❌ **不创建任何档案数据**（那是 T-011「新增学员」）
- ❌ 不做「我的资料」（T-013）
- ❌ 不改表结构　❌ 不写测试（AGENTS.md §11）
- ❌ `languages` **不拆成多对多表**（数据库设计就是逗号分隔字符串，照原样映射）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `entity/StudentProfile.java` | 新建 |
| `entity/Level.java` | 新建 |
| `repository/StudentProfileRepository.java` | 新建 |
| 任务书 + `docs/plan.md` + `docs/09` | 新建 / 更新 |

**不碰任何已有代码。**

---

## 4. 实现步骤

1. 新建 `Level` 枚举（BEGINNER / INTERMEDIATE / CONTEST，带中文 label）
2. 新建 `StudentProfile` 实体（11 个字段 + 两个时间戳）
3. 新建 `StudentProfileRepository`（**不加自定义方法**：主键就是 user_id，`findById` 已够用）
4. 编译 → 启动（验证 `ddl-auto: validate`）+ **负向测试**
5. 更新文档

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | ⚠️ `join_date` 是 DATE，必须用 `LocalDate` | 已按此实现，注释写明 |
| 2 | ⚠️ 表里现在 **0 行** | 「真能读出档案」的验证留到 **T-011 / T-013**；本任务**不谎称测过** |
| 3 | 🔒 `qq` / `wechat` / `email` 也是联系方式 | 已在注释里写明「**与手机号同等对待，不得出现在公开页面**」（从 spec §3.3 红线延伸） |
| 4 | 一对一方式选「裸 Long」 | 需要 `profile.getUser()` 时再升级，改动局部 |

---

## 6. 验证命令

`mvn -B -ntp compile` → `mvn -B -ntp spring-boot:run`

**关键判据**：应用能启动 = `ddl-auto: validate` 通过 = Hibernate 逐个核对了「Java 字段 ↔ 真实表列」。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动

| 文件 | 动作 | 大小 |
|---|---|---|
| `entity/Level.java` | 新建 | 718 字节 |
| `entity/StudentProfile.java` | 新建 | 3353 字节 |
| `repository/StudentProfileRepository.java` | 新建 | 646 字节 |

### 验证结果（真实输出）

| 验证 | 真实结果 |
|---|---|
| `mvn compile` | ✅ `BUILD SUCCESS`，**14** 个源文件（T-008 时是 11 个） |
| **正向**：启动 | ✅ `Started FountainHubApplication in 3.97 seconds` → **`validate` 通过**（Java 字段与真实表列全部对上） |
| **负向**：临时把 `major_class` 写成 `major_class_XX` | ✅ **启动失败**，报 ⭐ `Schema-validation: missing column [major_class_xx] in table [student_profiles]` |
| 还原 | ✅ sha256 前 12 位 `b2ba4f83866d`，与原始**完全一致**；重新编译 BUILD SUCCESS |

**负向测试的意义**：证明 `validate` **确实在逐列核对**，而不是"没检查所以没报错"。
所以"启动成功"这件事才真正等于"映射正确"。

### 遇到的问题

| # | 问题 | 说明 |
|---|---|---|
| 1 | 我自己的判定正则写错了大小写 | Hibernate 把列名报成小写 `major_class_xx`，我却在脚本里匹配 `major_class_XX`，一度误判为"失败原因不对"。**实际负向测试完全成功** —— 记录在此，避免以后误导 |

---

## 8. 遗留与下一步

- ⏸️ 档案数据的真实读写 → **T-011 / T-013**
- ➡️ 下一步：**T-010** 学员列表（`/admin/students`）—— **第一张有真实数据的后台表格** 📊
