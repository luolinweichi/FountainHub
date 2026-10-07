# task-018 · 考勤地基：`AttendanceEvent` + `AttendanceRecord` 实体 + 仓库

| 项 | 值 |
|---|---|
| 任务编号 | **T-018** |
| 任务名 | 考勤地基（两个实体 + 两个仓库） |
| 对应 | **M6 考勤签到 + 绿色热力图**（第一个任务） |
| 状态 | ✅ **已完成**（2026-10-03） |

---

## 1. 任务理解

把 `attendance_events` + `attendance_records` 两张表映射成 Java 实体 + 建仓库。**只打地基，不做页面。**

### ⚠️ 先回顾：T-018 原本不是这个内容

2026-10-03 项目负责人澄清了 M6 的真实需求 —— **「打卡」和「热力图」是两件独立的事**：

| | 原计划（错的） | 澄清后（对的） |
|---|---|---|
| **打卡** | 学员自己填「今天做了几道题」 | **「你到场了没有」** = **考勤签到** |
| **热力图** | 显示自己填的数 | 显示**做题量**，数据来自**内置题库** |

所以 **T-018 从「Checkin 实体」改成了「考勤地基」**；`Checkin` 实体挪到 **T-021**（做热力图时一起做）。
详见 [plan.md](../plan.md) 变更记录 v0.22 与 [docs/09](../09-开发进度日志.md) 的 M6 澄清条目。

### 两张表（真实数据库结构）

```
attendance_events（考勤活动）
  id / title / event_date / type(WEEKLY_MEETING|DAILY_OPEN) / description / created_by / created_at
  🔑 UNIQUE (event_date, type)   —— 同一天同一种活动只能有一场

attendance_records（考勤记录）
  id / event_id / user_id / status(PRESENT|ABSENT|LEAVE)
  checkin_type(SELF|ADMIN) / operator_id / checkin_at
  🔑 UNIQUE (event_id, user_id)  —— 同一活动同一人只有一条
  FK event_id → attendance_events CASCADE，FK user_id → users CASCADE
```

---

## 2. 非目标

- ❌ 签到页面（**T-019**）
- ❌ 后台考勤管理 / 代打卡 / 出勤率（**T-020**）
- ❌ 热力图与 `Checkin` 实体（**T-021**）
- ❌ 导出 Excel（**T-022**）
- ❌ **不改数据库**（表早就建好了，一行 SQL 都没执行）

---

## 3. 修改的文件

| 文件 | 动作 |
|---|---|
| `entity/AttendanceType.java` | **新建**（WEEKLY_MEETING / DAILY_OPEN） |
| `entity/AttendanceStatus.java` | **新建**（PRESENT / ABSENT / LEAVE） |
| `entity/CheckinType.java` | **新建**（SELF / ADMIN） |
| `entity/AttendanceEvent.java` | **新建** |
| `entity/AttendanceRecord.java` | **新建** |
| `repository/AttendanceEventRepository.java` | **新建** |
| `repository/AttendanceRecordRepository.java` | **新建** |
| 任务书 + plan.md + docs/09 | 更新 |

> ✅ **没有碰任何已有代码**

---

## 4. 关键设计

### 4.1 三个枚举（照抄数据库注释里的值）

和 `Role` / `UserStatus` 一样的做法：`@Enumerated(EnumType.STRING)`。
**好处**：写错一个字母**编译就报错**，而不是等运行时才发现。

> ⚠️ 枚举名必须和数据库注释**一字不差** —— 改一个字母，已有的数据就读不出来了。

### 4.2 ID 一律用裸 Long，不用 @ManyToOne

`event.createdBy` / `record.eventId` / `record.userId` / `record.operatorId` 全部是裸 `Long`。
理由：① 和 `User.groupId` / `StudentProfile` 风格一致；② **避免在模板里踩懒加载的坑**（R-012 已关掉 OSIV）。
真要显示「谁代打的」，T-020 一次性把人名查出来做成 Map 即可。

### 4.3 checkin_at 让数据库填

`insertable = false, updatable = false` —— 需求要的是「记录**何时**打的」，
由数据库在插入那一刻自动记下来，比应用层传时间更可靠（不怕时区、也防伪造）。

### 4.4 仓库只加马上要用的 4 个方法

```java
// 建活动前查重（表上有 UNIQUE (event_date, type)）
Optional<AttendanceEvent> findByEventDateAndType(LocalDate eventDate, AttendanceType type);
// T-019：看本周有哪些日常开放活动
List<AttendanceEvent> findByEventDateBetweenOrderByEventDateAsc(LocalDate from, LocalDate to);
// T-019：判断「我这场签过没」（也是那条唯一约束的用法）
Optional<AttendanceRecord> findByEventIdAndUserId(Long eventId, Long userId);
// T-019：我的考勤记录
List<AttendanceRecord> findByUserIdOrderByCheckinAtDesc(Long userId);
```

> ⚠️ **派生查询用的是 Java 属性名，不是数据库列名**。
> 这个坑在 T-016 踩过一次（`existsByLeaderUserId` 写错导致应用起不来）—— 这次特别小心。

---

## 5. 风险与不确定点

| # | 风险 | 处置 |
|---|---|---|
| 1 | 列名/类型对不上 → 启动直接失败 | `ddl-auto: validate` 是自动测试；**并做了负向测试**证明它真在检查 ✅ |
| 2 | 枚举值必须和数据库注释一字不差 | 照抄 ✅ |
| 3 | 派生查询方法名写错 | 只用 Java 属性名（eventDate / eventId / userId / checkinAt）✅ |
| 4 | 不确定点 | `attendance_events.created_by` 用裸 Long；T-020 若要显示「谁创建的」，届时再取名字 |

---

## 6. 验证命令

```cmd
cd /d D:\Project\FountainHub
mvn -B -ntp compile
mvn -o -B -ntp spring-boot:run
```

> ⚠️ **必须加 `-o`（离线模式）**：DSH 沙箱不允许 Maven 写 `.m2`，
> 不加 `-o` 会报 `AccessDeniedException: ...resolver-status.properties` 而启动不了（T-015 踩出来的）。

---

## 7. 完成记录（真实结果）

**完成日期**：2026-10-03

### 实际改动

| 文件 | 动作 |
|---|---|
| `entity/AttendanceType.java` / `AttendanceStatus.java` / `CheckinType.java` | 新建 |
| `entity/AttendanceEvent.java` / `AttendanceRecord.java` | 新建 |
| `repository/AttendanceEventRepository.java` / `AttendanceRecordRepository.java` | 新建 |

### 验证结果（真实输出，离线模式，独立实例 8081）

```
mvn compile  →  BUILD SUCCESS

验证 1（正常启动）
  启动成功: ✅
  Schema-validation 报错: ✅ 无  → AttendanceEvent / AttendanceRecord 与表逐列对得上
  沙箱 AccessDenied: ✅ 没有

验证 2（负向测试：把 checkin_type 改成 checkin_type_typo）
  启动成功: ✅ 启动失败（正是期望的）
  出现 Schema-validation 报错: ✅ 有  → 证明 validate 真的在检查这两个实体
  沙箱 AccessDenied: ✅ 没有（结论可信）

验证 3（还原后再启动）
  启动成功: ✅     Schema-validation 报错: ✅ 无
  还原的文件与原来逐字节一致 ✅
```

### ⚠️ 验证边界（如实说明）

本任务**没有页面**，两张表也都是**空的** → **无法端到端验证「真的读出了数据」**。
能验证的是「**映射和表结构完全对得上**」—— 这正是本任务的主要风险。
真正第一次用到它是在 **T-019**（学员签到）。

---

## 8. 遗留与下一步

- ➡️ 下一步：**T-019** 学员自助签到（`/me/attendance`）——
  看到今天/本周的日常开放活动，**到场点一下就签到**；没去就空着。
