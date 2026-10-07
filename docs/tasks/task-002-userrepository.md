# task-002 · UserRepository（按用户名 / 学号 / 手机号查询）

| 项 | 值 |
|---|---|
| 任务编号 | **T-002** |
| 任务名 | \`UserRepository\`：能按用户名 / 学号 / 手机号查询 |
| 对应 | **里程碑 2 收尾**；M1「账号与登录」的地基 |
| 状态 | ✅ **已完成**（2026-10-02） |
| 开始日期 | 2026-10-02 |
| 关联 | [plan.md](../plan.md) · [spec.md](../spec.md) 第 4.2 节 T1 · [05-数据库设计说明书](../05-数据库设计说明书.md) |

---

## 1. 目标

建一个 Spring Data JPA 仓库接口，提供 3 个查询能力：

| 方法 | 生成的 SQL | 服务于 |
|---|---|---|
| \`findByUsername\` | \`WHERE username = ?\` | T-006 登录方式之一 |
| \`findByStudentNo\` | \`WHERE student_no = ?\` | T-006 登录方式之一 |
| \`findByPhone\` | \`WHERE phone = ?\` | T-006 登录方式之一 |

**判定标准**：应用能正常启动（= Spring Data 成功把这 3 个方法名解析成了对实体属性的查询）。

---

## 2. 非目标（本次明确不做）

- ❌ 不写登录逻辑 / Spring Security（T-004 ~ T-006）
- ❌ 不写 Service 层（P1 不需要）
- ❌ 不写 \`DataInitializer\`（T-003）
- ❌ 不写任何 Controller / 页面
- ❌ **不改数据库表结构**
- ❌ 不写单元测试（AGENTS.md §11：P1 暂缓）
- ❌ **不加** \`count()\` / \`existsBy*\` —— 等 T-003 真需要时再加，不做"顺手的事"（AGENTS.md §4）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| \`src/main/java/com/fountainhub/repository/UserRepository.java\` | **新建**（同时新建 \`repository\` 包） |
| \`docs/tasks/task-002-userrepository.md\` | 本文件 |
| \`docs/plan.md\` | T-002 → ✅ + 验收证据 |
| \`docs/09-开发进度日志.md\` | 新增 T-002 条目 |

**不碰任何已有代码。**

---

## 4. 实现步骤

1. 新建包 \`com.fountainhub.repository\`
2. 新建接口 \`UserRepository extends JpaRepository<User, Long>\`
3. 声明 3 个派生查询方法（返回 \`Optional<User>\`）
4. 写清注释：为什么是接口、为什么用 Optional、方法名用 Java 属性名而非列名
5. 编译 + 启动验证
6. 更新文档

---

## 5. 风险与不确定点

| # | 风险 | 说明与对策 |
|---|---|---|
| 1 | ⚠️ **方法名写错 = 启动直接崩** | 最易错的是把 \`studentNo\` 写成列名 \`student_no\`。Spring Data 用的是 **Java 属性名**。写错报 \`PropertyReferenceException\` |
| 2 | ⚠️ **\`users\` 表当前 0 行** | 本任务**无法证明"真能查出人"**。这一步留到 **T-003**（\`DataInitializer\` 会真的调用它）。**本任务书不作超额声明** |
| 3 | ⚠️ \`studentNo\` / \`phone\` 可为 NULL | 数据库 UNIQUE **允许多个 NULL**；传 null 会生成 \`= null\`（永远匹配不到，且不报错）。已写进注释，提醒 T-006 先判空 |
| 4 | 不确定点 | \`existsByUsername\` 等是否现在就要 —— **本次不加**，等 T-003 |

---

## 6. 验证命令

\`\`\`cmd
cd /d D:\Project\FountainHub
mvn -B -ntp compile
\`\`\`
→ 应看到 **BUILD SUCCESS**

\`\`\`cmd
mvn -B -ntp spring-boot:run
\`\`\`
→ 应看到 **Started FountainHubApplication in x.x seconds**

**⚠️ 关键判据**：Spring Data 在**启动时**就解析这 3 个方法名。方法名与实体属性对不上时，应用会**直接启动失败**并报：

\`\`\`
PropertyReferenceException: No property 'xxx' found for type 'User'
\`\`\`

→ **所以"能正常启动"本身就是强证据**。

**需要目视确认**：控制台没有红色 \`ERROR\` / \`PropertyReferenceException\`。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-02

### 实际改动的文件

| 文件 | 动作 | 大小 |
|---|---|---|
| `src/main/java/com/fountainhub/repository/UserRepository.java` | 新建（含 `repository` 包） | 2219 字节 |
| `docs/tasks/task-002-userrepository.md` | 本文件 | 2426 字节 |
| `docs/plan.md` | T-002 → ✅ | — |
| `docs/09-开发进度日志.md` | 新增条目 | — |

**未改动任何已有代码。**

### 验证结果（真实输出）

| 命令 | 结果 |
|---|---|
| `mvn -B -ntp compile` | ✅ `BUILD SUCCESS`，编译 **6** 个源文件（T-001 时是 5 个） |
| `mvn -B -ntp spring-boot:run` | ✅ `HikariPool-1 - Start completed.` → `Initialized JPA EntityManagerFactory` → `Started FountainHubApplication in 9.656 seconds` |
| `/ping` | ✅ HTTP 200 |

### ⭐ 关键证据：负向测试（本任务最重要的一步）

第 6 节说「启动成功 = 方法名解析正确」—— 这句话**不能只靠假设**，所以做了一次**负向测试**：

1. 临时往接口里加一个**故意写错**的方法：`Optional<User> findByStudent_no(String)`（用了数据库列名 `student_no` 而不是 Java 属性名 `studentNo`）
2. 重新启动应用 → **启动失败**（真实输出，原文照抄）：

       Error creating bean with name 'userRepository' ...
       Could not create query for ... findByStudent_no(java.lang.String)
       No property 'student' found for type 'User'; Did you mean 'studentNo'
       Caused by: org.springframework.data.mapping.PropertyReferenceException:
                 No property 'student' found for type 'User'; Did you mean 'studentNo'

3. 删除错误方法，**文件还原为原版**（sha256 前 12 位 `a7dba58a0454`，还原前后**完全一致**）
4. 对最终版本重新编译 + 启动 → ✅ 全部通过（见上表）

**结论**：本任务的判定标准**成立且已被实测证明** —— Spring Data 确实在**启动时**解析派生查询方法名，写错会让应用直接起不来。当前 3 个方法名全部正确。

### 遗留与下一步

- ⏸️ **「真能查出人」尚未验证** —— `users` 表当时是 **0 行**，本任务不作超额声明。
  🔧 **2026-10-02 更正**：本行原先写「留给 **T-003**（`DataInitializer` 会真正调用 `findByUsername` / `findByStudentNo` / `findByPhone`）」—— **这个说法不准确**。
  T-003 的 `DataInitializer` 用的是 `JpaRepository` 自带的 `count()`，**并没有调用**这三个派生查询方法。
  这三个方法真正被使用、并得到「能查出真实数据」验证的地方是 **T-006（三种登录方式）**。
- ⏸️ `count()` / `existsBy*` 本次未加
- ➡️ 下一步：**T-003** `DataInitializer`（`users` 表为空时创建初始管理员 `admin` / TEACHER）

