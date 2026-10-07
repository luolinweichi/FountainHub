# T-001 · User 实体（把 users 表映射成 Java 类）

| 项 | 内容 |
|---|---|
| 任务编号 | **T-001** |
| 模块 | 阶段 0 · 里程碑 2 收尾 |
| 状态 | ✅ **已完成**（2026-10-02） |
| 开始日期 | 2026-10-02 |
| 验收标准 | 见 [../spec.md](../spec.md) 第 4.1 节「数据库里看不到明文密码」相关地基项；本任务自身的验收见下方第 6 节 |

---

## 1. 目标

把数据库的 `users` 表映射成一个 Java 类 `User`，让 JPA/Hibernate 能读写它。
**这是第一次让「Java 对象」和「数据库表」对应起来。**

## 2. 非目标（本任务明确不做）

- ❌ 不写 `UserRepository`（那是 T-002）
- ❌ 不写 `DataInitializer`（T-003）
- ❌ 不建其他实体（StudentProfile / StudyGroup / Checkin / News …）
- ❌ **不改数据库表结构**
- ❌ 不改 `application.yml`

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `pom.xml` | 改：新增 Lombok 依赖（**经项目负责人批准**，属 §6 的例外） |
| `src/main/java/com/fountainhub/entity/Role.java` | 新建：角色枚举（5 种） |
| `src/main/java/com/fountainhub/entity/UserStatus.java` | 新建：账号状态枚举（4 种） |
| `src/main/java/com/fountainhub/entity/User.java` | 新建：实体类 |
| `AGENTS.md` | 改：把 Lombok 使用规范写进 §6 |

## 4. 实现步骤

1. 在 `pom.xml` 加 Lombok（**不写版本号**，由父 POM 管理为 1.18.34）
2. 建 `entity` 包
3. 写 `Role` / `UserStatus` 枚举（配合 `@Enumerated(EnumType.STRING)`）
4. 写 `User`：`@Entity` + `@Table(name="users")` + 逐字段映射
5. `created_at` / `updated_at` 标 `insertable=false, updatable=false`（数据库自己填）

## 5. 风险与不确定点

| # | 风险 | 对策 |
|---|---|---|
| 1 | `ddl-auto: validate` 可能因 `TINYINT(1)` ↔ Java `Boolean` 的类型表述差异而报错（`logistics_duty`） | 预期 `tinyInt1isBit=true`（Connector/J 默认）会把它报告成 BIT，从而通过；若失败，改用 `@Column(columnDefinition="TINYINT(1)")` |
| 2 | JDK 21 会打一行注解处理警告 | 无害，仅 warning |
| 3 | Lombok 首次需要下载（本地 `.m2` 没有） | 走阿里云镜像 |

## 6. 验证命令

```cmd
cd /d D:\Project\FountainHub

REM ① 编译（会真的调用 Lombok 生成代码）
mvn -B -ntp compile

REM ② 反编译 class 文件，证明 getter/setter 真被生成了
javap -p -classpath target\classes com.fountainhub.entity.User

REM ③ 启动应用，让 Hibernate 拿实体核对真实表结构
mvn -B -ntp spring-boot:run
```

**通过标准**
- ① `BUILD SUCCESS`
- ② 输出里能看到 `getName()`、`setName(java.lang.String)`、`User()`
- ③ 出现 `HikariPool-1 - Start completed.` 且**无红色报错**

## 7. 决策记录

| 决策 | 内容 | 出处 |
|---|---|---|
| 用枚举而非字符串 | `Role` / `UserStatus` 用 Java 枚举 + `@Enumerated(EnumType.STRING)`；打错字编译就报错 | 项目负责人 2026-10-02 拍板 |
| **新增 Lombok 依赖**（§6 例外） | 依赖坐标 `org.projectlombok:lombok`，**不写版本**（父 POM 管理 = 1.18.34），`optional=true` | 项目负责人 2026-10-02 批准 |
| Lombok 使用规范 | **只允许**在实体类/DTO 上用 `@Getter` / `@Setter` / `@NoArgsConstructor` / `@AllArgsConstructor`；**禁止 `@Data`** | 项目负责人 2026-10-02 规定 |
| 角色只有 5 种 | 「社长(兼后勤)」不是独立角色，而是 `PRESIDENT`/`VICE_PRESIDENT` 上打 `logistics_duty` 标签（与 `sql/01-schema.sql` 一致） | 按数据库实际结构 |
| 本任务暂不用 `@AllArgsConstructor` | 13 个字段的位置参数构造器极易传错顺序；等真有调用方时再加 | 待项目负责人确认 |

---

## 8. 完成记录（真实结果）

**完成日期**：2026-10-02 ｜ **验证人**：AI 执行 + 项目负责人可复核

### 实际改动的文件

| 文件 | 动作 | 大小 |
|---|---|---|
| `pom.xml` | 改：+ Lombok 依赖（不写版本） | 4670 字节 |
| `src/main/java/com/fountainhub/entity/User.java` | 新建 | 4333 字节 |
| `src/main/java/com/fountainhub/entity/Role.java` | 新建 | 1424 字节 |
| `src/main/java/com/fountainhub/entity/UserStatus.java` | 新建 | 889 字节 |
| `AGENTS.md` | 改：新增 §6.1 Lombok 使用规范 | 9900 字节 |
| `docs/tasks/task-001-user实体.md` | 本文件 | — |

### 验证结果（真实输出，未加工）

| 命令 | 结果 |
|---|---|
| `mvn -B -ntp compile` | ✅ `BUILD SUCCESS`，编译 5 个源文件，9.6 秒 |
| `javap -p -classpath target\classes com.fountainhub.entity.User` | ✅ 完整列出 **13 个 getter + 13 个 setter**，证明 Lombok 生效 |
| `mvn -B -ntp spring-boot:run`（端口 8081） | ✅ `HikariPool-1 - Start completed.` + `Started FountainHubApplication in 3.522 seconds` + `/ping` 返回 HTTP 200 |

### 两条重要结论

1. ✅ **Hibernate 的 `ddl-auto: validate` 校验全部通过** —— `User` 实体与 `users` 表的 13 个字段映射**完全正确**。
   第 5 节担心的 `TINYINT(1)` ↔ `Boolean` 类型坑 **没有踩到**（Connector/J 默认 `tinyInt1isBit=true`，把 `TINYINT(1)` 报告成 BIT，Hibernate 接受了）。
2. ⚠️ **JDK 21 会打一行注解处理警告** —— 第 5 节的风险 2 **已命中**，属预期内 warning，不影响编译。

### 遇到的问题（如实记录）

| # | 问题 | 原因 | 处理 |
|---|---|---|---|
| 1 | 首次启动报 `APPLICATION FAILED TO START` / `Failed to start bean 'webServerStartStop'` | **端口 8080 被占用**（PID 9488，疑为 IDEA 里残留的旧进程）—— **与本次代码无关** | 改用 `--server.port=8081` 验证通过；8080 需项目负责人在 IDEA 里停止 |
| 2 | 我的验证脚本第一次找不到 `mvn` | DSH 进程环境变量早于 Maven 安装 | 从注册表重建 PATH（只影响我的脚本，不影响项目） |

### 遗留与下一步

- ⏸️ `@AllArgsConstructor` **本次未使用**（13 个字段的位置参数构造器极易传错顺序），等真有调用方时再加 —— 见第 7 节决策记录
- ➡️ 下一步：**T-002** `UserRepository`（按用户名 / 学号 / 手机号查询）

