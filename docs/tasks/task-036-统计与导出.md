# task-036 · 统计与导出（M11：补「导出」，并把 M11 的真实状态写对）

| 项 | 值 |
|---|---|
| 任务编号 | **T-036** |
| 任务名 | M11 统计与导出的**剩余部分**：导出学员名单 + 导出获奖清单（并抽公共 CSV 工具） |
| 页面/接口 | `/admin/export/students`（学员名单）· `/admin/export/awards`（获奖清单）· 总览页与学员列表页的导出入口 |
| 对应 | **M11**（FR-11.3） |
| 状态 | ✅ **已完成**（2026-10-04；完成记录见 §7） |
| 依赖 | 无新增依赖（**不引 Apache POI**，见 §5 风险 1） |

---

## 1. 目标（这个任务要达成什么）

### 1.1 ⚠️ 先纠正一个认识：M11 的大半**早就做完了**

开工前实测发现，`AdminHomeController`（类注释写着 **T-022 / FR-11.1**）**已经实现了**：

| 需求 | 实际状态（实测） |
|---|---|
| **FR-11.1** 社团总览：总人数 / 各组人数 / 本周打卡量 / 获奖总数 | ✅ **已完成（T-022）** —— `/admin` 上渲染 5 个 KPI（在社学员 / 本周做题 / 本周签到 / 考勤活动 / 获奖记录）+ 各组人数 |
| **FR-11.2** 打卡排行榜（不得含手机号） | ✅ **已完成（T-022）** —— `/admin` 上的「做题排行榜」，**只有姓名与题数** |
| **FR-11.3** 导出：考勤表 | ✅ 已完成（T-022），**产出为 CSV** |
| **FR-11.3** 导出：**学员名单** | ⬜ 未实现 ← **本次要做** |
| **FR-11.3** 导出：**获奖清单** | ⬜ 未实现 ← **本次要做** |
| FR-11.4 数据可视化大屏 | 二期，不做 |

也就是说：文档里长期写着「M11 ❓ 待商定」其实**只对"导出"那一半成立**。本次一并把文档改对。

### 1.2 本次交付

1. **`GET /admin/export/students`** —— 学员名单 CSV（**不含手机号**）
2. **`GET /admin/export/awards`** —— 获奖清单 CSV（**不含手机号**；带「同意公开人数 / 总人数」列）
3. **`util/CsvFile`** —— 把「往外写 CSV」收口成**一处**（BOM / 中文文件名 / 转义）
4. 总览页 `/admin` 与学员列表页 `/admin/students` 上的**导出入口按钮**
5. 文档同步：把 M11 的真实状态写对（`01` / `03` / `08` / `README` / `CODE_MAP` / `TEST_PLAN` / `plan`）

---

## 2. 非目标（本次明确不做）

- ❌ **不做** FR-11.4 数据可视化大屏（二期）
- ❌ **不动** `AdminHomeController` 已有的统计逻辑（总览与排行榜保持原样）
- ❌ **不引入 Apache POI**：沙箱**没有外网**、本地 Maven 仓库里**没有 POI**（实测 `org/apache/poi` 目录不存在），依赖加不上 → 统一导 **CSV（UTF-8 BOM）**
- ❌ **不改数据库结构**、**不改权限模型**（沿用 `hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')`，**组长一律 403**）
- ❌ **不导**手机号 / QQ / 微信 / 邮箱（红线；见 §5 风险 3）
- ❌ 不做"按当前筛选条件导名单"（导出就是**全量**，见 §4 步骤 5 的理由）

---

## 3. 计划修改的文件

| 文件 | 动作 |
|---|---|
| `util/CsvFile.java` | 🆕 新建：CSV 下载工具（UTF-8 BOM + RFC5987 中文文件名 + RFC4180 转义） |
| `controller/StudentExportController.java` | 🆕 新建：`/admin/export/students` |
| `controller/AwardExportController.java` | 🆕 新建：`/admin/export/awards` |
| `controller/AttendanceExportController.java` | 改：**只把"往外写"那段搬走**，改用 `CsvFile`（表格算法 `buildTable` 一行未动） |
| `templates/admin/index.html` | 改：新增「导出名单」卡片（两个按钮 + 说明） |
| `templates/admin/students.html` | 改：筛选表单右侧加「导出名单」按钮 |
| 文档 7 份 | `plan.md` / `01` / `03` / `08` / `README` / `CODE_MAP` / `TEST_PLAN` + `docs/09` DEVLOG |

---

## 4. 实现步骤

1. **读实体定列**：`User`（realName / studentNo / groupId / status）、`StudentProfile`（grade / majorClass / joinDate / languages / level）、`Award` + `AwardStudent`。
2. **抽 `CsvFile`**（从 `AttendanceExportController` 里的 `csv()` 与 `escape()` 提出，行为照旧）。
3. **改考勤导出**用新工具 → **拉接口比对改动前后的字节**（见 §6 验证 ①）。
4. **写学员名单导出**：一次读完全部用户 + 档案 + 组名，在内存里拼表（避免 N+1）。
5. **写获奖清单导出**：一条奖一行，成员姓名用「、」连接；另给「同意公开人数 / 总人数」。
6. **加页面入口**：总览页新增「导出名单」卡片；学员列表页按钮放在筛选表单右侧（`margin-left:auto`）。
   ⚠️ 学员列表页的导出是**全量**，不跟随上面的筛选条件 —— 否则"筛完才发现导少了"。
7. **文档同步** + 本任务书 + DEVLOG。
8. 编译 + 8081 手动验证（**用后关实例**）。

---

## 5. 风险与不确定点

1. **格式只能是 CSV**（不是真 `.xlsx`）：POI 加不上。若项目负责人愿意在 IDEA 里**联网**加 `poi-ooxml`，则**只改 `CsvFile` 一个类**，三个导出控制器一行都不用动。
2. **导出的列**：学员名单 = 姓名 / 学号 / 组别 / 状态 / 年级 / 专业班级 / 入社日期 / 擅长语言 / 阶段；获奖清单 = 比赛名称 / 级别 / 奖项等级 / 获奖日期 / 举办方 / 指导老师 / 获奖学员 / 同意公开人数 / 总人数。
3. **🔒 隐私**：两份文件都**不含手机号**（也不含 QQ / 微信 / 邮箱），验证里用正则 `1[3-9]\d{9}` 断言命中 0 处。
4. **获奖清单与"公开意愿"的关系**（需要留意，但本次口径已定）：红线约束的是**对外展示**（`/awards` 展示墙、首页「获奖公示」，由 `PublicAwardService` 统一过滤）。本导出是**后台功能**（只有社长层 / 老师能下载，用于向学校报送），所以**两条都导**，但给「同意公开人数 / 总人数」列，让人一眼看出哪些能对外发。
   → 若项目负责人认为"交给学校"也算对外，把 `AwardExportController` 改成只导 `isPublic=1` 即可（改动很小）。
5. **重构已验收的考勤导出**：用「**输出逐字节一致**」来证明没改坏（`buildTable` 一行没动）。
6. **两个考勤导出的 ASCII 兜底文件名**由 `attendance-<日期>.csv`（两个接口一样）改成 `attendance-weekly-<日期>.csv` / `attendance-all-<日期>.csv` —— **这是顺手修掉的一个小毛病**（原来两个接口下载下来同名，会互相覆盖）。

---

## 6. 验证命令（照抄可跑）

```powershell
# ① 编译
mvn -o -B compile

# ② 起独立实例（用后关；项目负责人的实例在 8080，验证用 8081）
mvn -o -B -ntp spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"

# ③ 用 teacher 登录后分别拉四个接口，断言：
#    · 考勤 weekly / all 的【响应体字节】与重构前完全一致（SHA256 比对）
#    · 学员名单 / 获奖清单：Content-Type=text/csv、首 3 字节是 UTF-8 BOM(EF BB BF)
#    · 四份文件的正文里，手机号正则 1[3-9]\d{9} 命中 0 处
# ④ 页面：/admin 与 /admin/students 都能看到导出链接
# ⑤ 权限：用 student 账号访问两个新接口 → 403
```

---

## 7. 完成记录

**状态**：✅ **已完成**（2026-10-04；编译 BUILD SUCCESS、**18 项验证全通过**）

### 7.1 实际改动的文件

| 文件 | 动作 |
|---|---|
| `util/CsvFile.java` | 🆕 新建（BOM + RFC5987 文件名 + RFC4180 转义 + `text()` 空值便利方法） |
| `controller/StudentExportController.java` | 🆕 新建（9 列，`@PreAuthorize` 类级） |
| `controller/AwardExportController.java` | 🆕 新建（9 列，含「同意公开人数 / 总人数」） |
| `controller/AttendanceExportController.java` | 改：`csv()` / `escape()` 删除 → 改用 `CsvFile`；**`buildTable()` 一行未动** |
| `templates/admin/index.html` | 改：新增「导出名单」卡片 |
| `templates/admin/students.html` | 改：筛选表单右侧加「导出名单」按钮 |

### 7.2 验证结果（真实输出）

```
① 回归：考勤导出【重构前后逐字节一致】
   /admin/attendance/export/weekly  SHA256前16 = 02BA72689748A417  ✅ 与基准一致
   /admin/attendance/export/all     SHA256前16 = 036B82630E5DAE5C  ✅ 与基准一致
② 四个接口：Content-Type = text/csv;charset=UTF-8，正文首 3 字节 = EF BB BF（UTF-8 BOM）✅
③ 🔒 手机号正则 1[3-9]\d{9}：学员名单 0 处、获奖清单 0 处 ✅（正文里连"手机"字样都没有）
④ 表头逐字核对通过：
   学员名单：姓名,学号,组别,状态,年级,专业班级,入社日期,擅长语言,阶段
   获奖清单：比赛名称,级别,奖项等级,获奖日期,举办方,指导老师,获奖学员,同意公开人数,总人数
⑤ 真实数据抽样（学员名单）：
   luo,005,未分组,在读,2024级,25班,2026-10-02,C++,
   普通社员,2025001,Python 组,在读,2025级,计算机 1 班,2025-09-01,"Python,C++",入门
   ← 注意 "Python,C++" 被【正确加了引号】（RFC4180 转义生效）
⑥ 获奖清单的取数路径：库里本来是空的（只有表头），临时插入 1 条奖 + 2 名成员
   （user 3 同意公开、user 4 不同意）后实测输出：
   （临时）测试杯程序设计赛,国家级,一等奖,2026-10-01,测试组委会,王老师,普通社员、luo,1,2
   ← 级别/等级/日期/举办方/指导老师/两名成员姓名都正确；「同意公开 1 / 共 2」
     （**未同意者的姓名也在内**，这是后台导出、用于向学校报送；同时给出公开人数供人判断）
   ✅ **测完立刻删除**：awards=0、award_students=0（级联删除生效），拉接口确认回到"只有表头"
⑦ 页面入口：/admin 有「导出 · 学员名单」「导出 · 获奖清单」；/admin/students 有「导出名单」✅
⑧ 权限：student 账号访问 /admin/export/students 与 /admin/export/awards 均 = 403 ✅
────────────────────────────────────────────────
合计：18 项断言全部 PASS，0 FAIL
```

### 7.3 ⚠️ 过程中踩的一个坑（记下来，别再犯）

第一次插测试数据时，我用 `SELECT LAST_INSERT_ID();` 去拿刚插入的获奖 id ——
但**每一条 mysql 命令都是一个新连接**，而 `LAST_INSERT_ID()` 是**连接级**的，新连接里必然是 **0**。
后果：关联行插到了 `award_id=0`（被外键拒绝，而我把 stderr 丢掉了没看见），
清理时 `DELETE ... WHERE id=0` 也没删掉那条奖 → **库里残留了 1 条测试奖**。
修正做法：**先用 `SELECT id FROM ... WHERE 比赛名 LIKE ...` 查出真实 id 再操作**，并且**不要吞 stderr**。
最终已清理干净（awards=0、award_students=0）。
副作用（无害）：`awards` 表的 AUTO_INCREMENT 前进到了 20。

### 7.4 未做（明确留白）

- 真 `.xlsx` 导出（等 POI 依赖就位；**只改 `CsvFile` 一个类**）
- FR-11.4 数据可视化大屏（二期）
- 按筛选条件导出（现在是全量导出）

> 📌 2026-10-04 文档 ↔ 实际项目对齐时复核并回填本记录（只改文档、不动代码）。
