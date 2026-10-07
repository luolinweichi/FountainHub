# CODE_MAP.md · 代码地图与功能清单

> **生成日期**：2026-10-02 ｜ **最后对齐**：2026-10-04 ｜ **方式**：全量只读盘点（**未改动任何代码**）
> **配套**：[TEST_PLAN.md](TEST_PLAN.md)（手动测试手册）· [spec.md](spec.md)（编码契约）· [文档索引](README.md)
> **权威关系**：本文件是「**代码现状**」的快照；一期范围以 [spec.md](spec.md) 为准；任务状态以 [plan.md](plan.md) 为准。

---

## 0. 一句话现状

**FountainHub** 是一个校园社团的内部管理后台。已完成 **里程碑 1~3**，以及
**账号与登录**、**成员档案**、**分组管理**、**考勤签到**、**获奖**、**新闻公告**、**公开首页**、**统计与导出**、**站点设置**。

| 规模 | 值 |
|---|---|
| Java | **81 个文件 / 约 5,410 行**（注释占比高，是刻意的——见 §8.6 O4） |
| 模板 | **31 个** Thymeleaf 页面（3,018 行） |
| Controller | **29 个文件**（28 个 `@Controller` + 1 个全局异常处理 `@ControllerAdvice`） |
| 实体 / 枚举 | **13 个实体 + 10 个枚举 = 23 个文件** |
| 仓库 | **14 个**（13 个 Repository + 1 个 Specification） |
| 服务 / 工具 / 配置 | 3 / 4 / 4 |
| 数据库 | **20 张表**（`01-schema.sql` 19 张 + `04` 的 `site_settings`） |
| 静态资源 | **5 个文件**（1 个 CSS + 4 个原生 JS，**零第三方 JS 库**） |

**能跑、能登录、能注册、能管成员（含手工补录）、能分组、能考勤签到、能发新闻、能录获奖并对公众展示、能导出名单/获奖清单/考勤表。**

---

## 1. 文件树与作用

```
FountainHub\
├─ README.md                    ← 项目入口（对外门面）
├─ pom.xml                      ← Maven 构建文件（依赖 + Java 21 + 打包）
├─ .gitignore                   ← 忽略 target/、.idea/、.installer/、deploy/、uploads/、application-local.yml、docs-local/
│
├─ docs\                        ← 【所有文档都在这，索引见 docs/README.md】
│   ├─ 00-快速开始.md             ← 跑起来 + 「改哪几处 = 变成你社团的」
│   ├─ 01-需求规格说明书.md        ← 需求全貌（FR 编号的出处）
│   ├─ 02-技术路线选型.md          ← 为什么选 Java（含与 Python 路线的对比）
│   ├─ 03-一期P1实现清单.md        ← P1 要做什么（plan.md 的来源）
│   ├─ 04-部署方案.md             ← 怎么部署到服务器
│   ├─ 05-数据库设计说明书.md       ← **20 张表**的设计与关系
│   ├─ 06-开发环境准备.md          ← 怎么把环境装起来
│   ├─ 08-视觉与品牌规范.md        ← 【UI 设计系统契约】色彩 token / 页面清单 / 组件规范
│   ├─ 09-开发进度日志.md          ← 【= DEVLOG】每一步做了什么、踩了什么坑（按时间倒序）
│   ├─ 10-题库API契约.md           ← 二期题库接口契约（一期只写契约）
│   ├─ 12-开源定位与对外说明.md     ← 这个项目是什么、给谁用、不是什么
│   ├─ spec.md                    ← 【编码契约】做什么 / 不做什么 / 验收标准
│   ├─ plan.md                    ← 【任务总表】T-001 ~ T-045
│   ├─ CODE_MAP.md                ← 本文件
│   ├─ TEST_PLAN.md               ← 手动测试手册
│   ├─ README.md                  ← 文档索引
│   └─ tasks\                     ← 一任务一文件的任务书（**39 份 + README**）
│
├─ sql\                          ← 数据库脚本
│   ├─ 01-schema.sql             ← 建 **19 张表**（含外键、索引、唯一约束）
│   ├─ 02-seed.sql               ← 种子数据（1 个示例组 / 3 条示例新闻 / 2 个章节；**不含用户**）
│   ├─ 03-add-password-reset-requests.sql ← 增量（幂等）：给老库补"忘记密码"那张表
│   ├─ 04-add-site-settings.sql  ← 增量（幂等）：第 **20** 张表 site_settings
│   ├─ 05-add-checkins.sql       ← 增量（幂等）：给老库补回 checkins（二期题库用）
│   └─ 06-add-must-change-password.sql ← 增量（幂等）：🆕 2026-10-07，users 加 must_change_password 列（首次登录强制改密）
│
├─ src\main\java\com\fountainhub\
│   ├─ FountainHubApplication.java   ← 【入口】@SpringBootApplication + main()
│   ├─ config\                        ← 【4 个】
│   │   ├─ SecurityConfig.java        ← 安全总配置：白名单 / 角色规则 / 403 / PasswordEncoder
│   │   ├─ DataInitializer.java       ← 启动钩子：空库时建 3 个测试账号（🔐 密码走**环境变量**）
│   │   ├─ GlobalModelAdvice.java     ← @ControllerAdvice：全站公共数据（系统名 / 社团名 / 口号 / currentPath / 当前用户姓名）
│   │   └─ WebMvcConfig.java          ← MVC 配置（上传目录等静态资源映射）
│   ├─ controller\                    ← 【29 个文件 = 28 个 @Controller + 1 个 @ControllerAdvice】
│   │   ├─ HomeController.java                 ← /
│   │   ├─ HealthController.java               ← /ping
│   │   ├─ AboutController.java                ← /about（社团介绍 / 招新页）
│   │   ├─ PublicNewsController.java           ← /news · /news/{id}
│   │   ├─ PublicAwardController.java          ← /awards（获奖展示墙，游客可看）
│   │   ├─ LoginController.java                ← /login
│   │   ├─ ErrorPageController.java            ← /403
│   │   ├─ RegisterController.java             ← /register（公开自助注册）
│   │   ├─ ForgotPasswordController.java       ← /forgot（忘记密码申请）
│   │   ├─ AdminPasswordResetController.java   ← /admin/students/reset/{id}/approve|reject
│   │   ├─ AdminStudentController.java         ← /admin/students/**（列表 / 详情 / 编辑 + 手工补录）
│   │   ├─ AdminStudentAccountController.java  ← /admin/students/{id}/审核·状态·角色（细粒度权限）
│   │   ├─ AdminGroupController.java           ← /admin/groups（建组 / 改组 / 定组长 / **加组员** / **删组**）
│   │   ├─ AdminAttendanceController.java      ← /admin/attendance（考勤活动 + 代打卡）
│   │   ├─ AttendanceExportController.java     ← /admin/attendance/export/weekly|all（**CSV**）
│   │   ├─ StudentExportController.java        ← /admin/export/students（**CSV**）
│   │   ├─ AwardExportController.java          ← /admin/export/awards（**CSV**）
│   │   ├─ AdminNewsController.java            ← /admin/news（发布 / 置顶 / 删除）
│   │   ├─ AdminAwardController.java           ← /admin/awards（录入 / 编辑 / 删除）
│   │   ├─ AdminSettingsController.java        ← /admin/settings（背景图上传 / 恢复默认）
│   │   ├─ AdminHomeController.java            ← /admin（后台总览）
│   │   ├─ UploadController.java               ← POST /admin/upload
│   │   ├─ MyHomeController.java               ← /me（我的主页：头像区 + 签到日历）
│   │   ├─ MyAttendanceController.java         ← /me/attendance（自助签到 + 签到日历 + 本周活动）
│   │   ├─ MyGroupController.java              ← /me/group（我的小组，组长专用）
│   │   ├─ MyAwardController.java              ← /me/awards（我的获奖 + 公开意愿开关）
│   │   ├─ ProfileController.java              ← /me/profile（我的资料）
│   │   ├─ PasswordController.java             ← /me/password（修改密码）
│   │   └─ GlobalExceptionHandler.java         ← @ControllerAdvice 全局异常兜底
│   ├─ dto\
│   │   └─ RegisterForm.java          ← 注册表单载体（避免 refill 收 13 个参数）
│   ├─ entity\                        ← 【23 个文件 = 13 实体 + 10 枚举】
│   │   ├─ User.java / StudentProfile.java / StudyGroup.java
│   │   ├─ News.java / NewsImage.java
│   │   ├─ AttendanceEvent.java / AttendanceRecord.java
│   │   ├─ Award.java / AwardStudent.java / AwardImage.java
│   │   ├─ UploadFile.java / PasswordResetRequest.java / SiteSetting.java
│   │   └─ 枚举 10 个：Role / UserStatus / Level / NewsCategory / NewsStatus /
│   │       CheckinType / AttendanceStatus / AttendanceType / AwardLevel / Prize
│   │       ⚠️ **`CheckinType` 是「考勤」用的**（`attendance_records.checkin_type` =
│   │          本人签到 / 社长代打卡）—— 名字与"学习打卡"像，但**完全无关**，别删。
│   ├─ repository\                    ← 【14 个】
│   │   ├─ UserRepository.java / UserSpecifications.java（动态筛选条件）
│   │   ├─ StudentProfileRepository.java / StudyGroupRepository.java
│   │   ├─ NewsRepository.java / NewsImageRepository.java
│   │   ├─ AttendanceEventRepository.java / AttendanceRecordRepository.java
│   │   ├─ AwardRepository.java / AwardStudentRepository.java / AwardImageRepository.java
│   │   └─ UploadFileRepository.java / PasswordResetRequestRepository.java / SiteSettingRepository.java
│   ├─ security\
│   │   ├─ DbUserDetailsService.java  ← 【登录的核心】按 用户名/学号/手机号 查人并校验；派生 ROLE_LEADER
│   │   └─ CurrentUserService.java    ← 取当前登录用户的**唯一入口**
│   ├─ service\                       ← 【3 个】业务服务层
│   │   ├─ FileStorageService.java    ← 文件存储（上传落盘 + 登记 upload_files）
│   │   ├─ PublicAwardService.java    ← 公开获奖**口径的唯一来源**（展示墙与首页共用）
│   │   └─ AttendanceCalendarService.java ← 签到日历的**数据装配**（`/me` 与 `/me/attendance` 共用）
│   └─ util\
│       ├─ FormUtils.java             ← blankToNull / trimToEmpty / parseEnum / parseLong
│       ├─ CalendarGrid.java          ← 日历格子的排版模型（服务端生成，不用图表库）
│       ├─ CsvFile.java               ← CSV 导出收口（BOM / 中文文件名 / 转义）
│       └─ UserDisplay.java           ← 姓名显示工具（对外展示收口）
│
├─ src\main\resources\
│   ├─ application.yml           ← 【主配置】端口 / 数据源 / JPA（ddl-auto=validate）/ 日志
│   ├─ application-dev.yml       ← dev profile：只含 spring.jpa / logging.level（**无任何账号密码项**）
│   ├─ application-local.yml     ← 🔒 【本机私有】数据库密码（**已被 .gitignore 忽略，绝不提交**）
│   ├─ templates\                ← 【Thymeleaf：31 个文件 = 29 个页面 + 2 个 fragments】
│   │   ├─ index / about / awards / 403 / login / register / forgot .html
│   │   ├─ news\{list,detail}.html
│   │   ├─ error\{404,500}.html
│   │   ├─ fragments\layout.html  ← 公共外壳 + 导航栏 + 页脚（**全站样式与 CDN 的唯一来源**）
│   │   ├─ fragments\icons.html   ← 内联 SVG 图标片段
│   │   ├─ admin\{index,students,student-new,student-detail,groups,attendance,attendance-detail,
│   │   │        news,news-form,awards,award-form,settings}.html   ← 12 个
│   │   └─ me\{index,attendance,awards,group,password,profile}.html ← 6 个
│   └─ static\                   ← 静态资源（**零第三方 JS 库**）
│       ├─ css\fh.css            ← 全站唯一一份 CSS（`--fh-*` 设计 token）
│       └─ js\{carousel,crop,md,upload}.js
```

**不纳入版本管理的目录（`.gitignore`）**：

| 目录 / 文件 | 为什么 |
|---|---|
| `target/` | Maven 编译产物 |
| `.idea/` | IDEA 自己的配置 |
| `uploads/` | 用户上传的图片（部署时单独备份） |
| `deploy/` | 部署包（含 jar 与本地配置） |
| `.installer/` | 本机一次性脚本与临时文件（含开发账号 SQL，**有条目含明文密码**） |
| `application-local.yml` | 🔒 数据库密码 |
| `docs-local/` | 🔒 **只留本机的文档**（内部交接、环境报告、设计稿归档、社徽素材等） |

---

## 2. 入口 / 命令 / 配置

| 项 | 值 |
|---|---|
| **入口类** | `com.fountainhub.FountainHubApplication` |
| **启动** | `mvn spring-boot:run`（或 IDEA 里运行入口类） |
| **构建** | `mvn -DskipTests package` |
| **编译（最常用）** | `mvn -B -ntp compile` |
| **测试** | ⚠️ `mvn test` —— **`src/test` 目录不存在，零测试类**（一期有意暂缓，以手动验证为准，见 [TEST_PLAN](TEST_PLAN.md)） |
| **静态检查** | 本项目不使用 Lint |
| **主配置** | [application.yml](../src/main/resources/application.yml) |
| **私有配置** | [application-local.yml](../src/main/resources/application-local.yml)（数据库密码，**不提交**） |
| **初始账号密码** | 🔐 **环境变量 `FH_INIT_PASSWORD`**（源码里**没有任何明文密码**；不设变量则不建账号） |
| **数据库脚本** | [01](../sql/01-schema.sql) → [02](../sql/02-seed.sql) → [03](../sql/03-add-password-reset-requests.sql) → [04](../sql/04-add-site-settings.sql) → [05](../sql/05-add-checkins.sql) → [06](../sql/06-add-must-change-password.sql)（全新安装照单全跑；**共 20 张表**）<br>`03`~`06` 全是**幂等增量**脚本（重复跑不报错、已存在时自动跳过）<br>🔴 **`06` 必须先跑脚本、再启动应用**：`ddl-auto: validate` 只校验不改表，缺列会导致**启动失败** |
| **应用端口** | 8080 |
| **数据库** | MySQL 8.0+（开发机实测 26.7），库名 `fountainhub` |

---

## 3. 已实现功能

图例：✅ 已完成并实测 ｜ 🟡 部分完成 ｜ ⏸️ 暂缓 ｜ ❌ 未做 ｜ 🚫 已取消

### 账号与登录

| FR | 内容 | 状态 | 实现位置 |
|---|---|---|---|
| FR-1.2 | 学号 / 手机号 / 用户名 **任一 + 密码** 登录 | ✅ | [DbUserDetailsService](../src/main/java/com/fountainhub/security/DbUserDetailsService.java) |
| FR-1.3 | 密码加密存储（BCrypt）+ Session 登录态 | ✅ | [SecurityConfig](../src/main/java/com/fountainhub/config/SecurityConfig.java) |
| FR-1.4 | 忘记密码 → 自助申请、社长审核 | ✅ 路径 `/forgot`；原「社长重置密码」🚫 已取消 | [ForgotPasswordController](../src/main/java/com/fountainhub/controller/ForgotPasswordController.java) + [AdminPasswordResetController](../src/main/java/com/fountainhub/controller/AdminPasswordResetController.java) |
| FR-1.5 | 手机号不外显 | 🟡 **修订**：公开页不可见、**管理员后台可见** | 列表页不查不显示；详情页显示 |
| FR-1.6 | 自行修改密码 | ✅ | [PasswordController](../src/main/java/com/fountainhub/controller/PasswordController.java) `/me/password` |
| FR-1.7 | 批量导入成员（Excel/CSV） | ❌ 一期未做（已有**手工补录**） | — |
| FR-1.8 | 账号状态：待审核/在读/退社/毕业/已禁用 | ✅ | [UserStatus](../src/main/java/com/fountainhub/entity/UserStatus.java) |
| FR-1.9 | **自助注册**（公开页） | ✅ | [RegisterController](../src/main/java/com/fountainhub/controller/RegisterController.java) |
| FR-1.10 | **管理员审核**（通过 / 拒绝） | ✅ | [AdminStudentAccountController](../src/main/java/com/fountainhub/controller/AdminStudentAccountController.java) |

### 成员档案

| FR | 内容 | 状态 | 实现位置 |
|---|---|---|---|
| FR-2.1 | 基本信息：姓名、学号、年级、专业/班级 | ✅（**性别未做**） | User + StudentProfile |
| FR-2.2 | 联系方式：手机号、QQ/微信、邮箱 | ✅ | User.phone + StudentProfile |
| FR-2.3 | 社团信息：入社时间、**所属组**、带组组长、在社状态 | ✅ | [StudyGroup](../src/main/java/com/fountainhub/entity/StudyGroup.java) + [AdminGroupController](../src/main/java/com/fountainhub/controller/AdminGroupController.java) |
| FR-2.4 | 技术水平：擅长语言、当前阶段 | ✅ | StudentProfile.languages / level |
| FR-2.5 | 登录账号唯一性校验 | ✅ | 注册 + 编辑时都查重 |
| FR-2.6 | 长期评语 | 🚫 **已取消** | `comments` 表保留但为空 |
| FR-2.7 | 自行修改联系方式 | ✅ **收紧后**：只能改 QQ/微信/邮箱，**手机号只能管理员改** | [ProfileController](../src/main/java/com/fountainhub/controller/ProfileController.java) |
| FR-2.8 | 头像上传 | ❌ 二期 | — |

### 其余模块

| 模块 | 状态 |
|---|---|
| 分组管理（FR-3.x） | ✅ **完成**：`/admin/groups` 支持 **建组 / 改组名说明排序 / 指定组长 / 添加组员 / 删除组**；成员列表可按组筛选；**组长只能看本组的姓名与人数** |
| 在线题库 / 外部 OJ | ❌ **二期**（5 张空表已建好）；⭐ **接口契约已定稿** → [10-题库API契约](10-题库API契约.md) |
| **考勤签到 + 签到日历**（FR-6.x） | ✅ **完成**：考勤地基 / 自助签到 + 签到日历 / 后台考勤管理 + 代打卡 / 出勤率与导出 |
| 作业 | ❌ 不做 |
| **获奖**（FR-8.x） | ✅ **完成**：获奖管理（团队奖一奖多人 + 每人独立公开意愿 + 证书多图）+ **获奖展示墙 `/awards`**（游客可看、只显示同意公开者、**不展示证书照片**）+ 首页「获奖公示」区块（与展示墙共用 `PublicAwardService`） |
| 新闻公告（FR-9.x） | ✅ **完成**：实体 → 后台管理 + 零依赖 Markdown 编辑器 → 图片上传 + 封面裁剪 → 对外公开新闻页 |
| 统计与导出（FR-11.x） | ✅ **完成**：`/admin` 总览（4 个 KPI + 各组人数）；三个 CSV 导出（学员名单 / 获奖清单 / 考勤表）。⚠️ **未引入 Apache POI → 全部是 CSV(UTF-8 BOM)**；🔒 **均不含手机号** |
| 公开首页（FR-12.x） | ✅ **完成**：首页接最新新闻 + `/about` 社团介绍页 + 全站手机号红线自查零泄漏 + 焦点图轮播与封面裁剪 + 视觉落地 |
| 忘记密码 | ✅ **完成**：`/forgot` 申请 + 后台审核（**是"申请+审核"，不是"重置"**） |
| 站点设置 | ✅ **完成**：`/admin/settings` 上传 / 恢复默认 + 背景图铺满「整个首页」（导航以下）+ 白色 40% 遮罩 |
| ⏸️ **学习热力图 / 做题排行榜** | 🚫 **一期撤销**（2026-10-04）。数据要等二期题库、且"其他社团用不上"→ 热力图、做题排行榜、`/me/study` 入口**全部下线**；**`checkins` 表保留**（二期题库契约依赖它），Java 侧实体已删（在 git 历史里） |

### 额外做的东西（不在 FR 里，但很有价值）

| 东西 | 说明 |
|---|---|
| **403 页面** | 越权时显示，而不是框架的白板错误页 |
| **细粒度权限** | 副社长不能改角色 / 禁用账号（`@PreAuthorize`） |
| **列表搜索 / 筛选 / 分页** | 组合条件用 `Specification` 拼装 |
| **参数消毒** | 分页参数有上限、非法枚举值兜底（不是 400 错误页） |
| **忘记密码申请 + 审核** | 提交申请 → 社长审核通过才写回 `users.password_hash`（页面文案也不许出现"重置"） |
| **获奖公开口径收口** | `service/PublicAwardService` 是公开获奖的**唯一来源** |
| **签到日历口径收口** | `service/AttendanceCalendarService` 让 `/me` 与 `/me/attendance` 用**同一份规则** |
| **零第三方 JS 库的前端** | Markdown 编辑器、封面裁剪（Canvas）、焦点图轮播、图片上传全部自己写 |

---

## 4. 未实现 / 半成品 / 写死 / 临时的地方

### 4.1 明确的半成品

| # | 地方 | 现状 | 谁来收尾 |
|---|---|---|---|
| 1 | **登录后不更新 `last_login_at`** | ⚠️ **仍然存在**：数据库有这个列、实体也映射了，但全项目**没有任何一处写过它**（详情页永远显示「从未登录过」） | 待排 |
| 2 | `comments` 表 | 建好了、**空的、功能已取消** | 不用管 |
| 3 | **考勤 / 名单导出是 CSV，不是 .xlsx** | ⚠️ 项目**刻意不引入 Apache POI** → 导出实现为 **CSV（UTF-8 BOM，Excel 可直接打开）**；将来若要 .xlsx 需先经批准加依赖 | 作者决定 |
| 4 | **二期题库** | 5 张空表已建好、接口契约已定稿，**但没有界面、没有 Java 实体** | 二期 |

### 4.2 写死 / 硬编码的地方

| # | 地方 | 写死了什么 | 评价 |
|---|---|---|---|
| 1 | `GlobalModelAdvice` | `SYSTEM_NAME` / `CLUB_NAME` / `CLUB_SLOGAN` | ✅ **合理**——这正是"全站唯一来源"的设计（见 [00-快速开始](00-快速开始.md)：改这几行就变成你自己的社团） |
| 2 | `DataInitializer` | 3 个测试账号的**用户名**（teacher / president / student） | ✅ 合理（就是它的职责）。🔐 **密码已改为读环境变量 `FH_INIT_PASSWORD`**，源码里**没有任何明文密码** |
| 3 | `DbUserDetailsService` | 登录白名单 `Set.of(ACTIVE, GRADUATED)` | ✅ 合理（业务规则，有注释说明） |
| 4 | `AdminStudentController` | `MAX_PAGE_SIZE = 50`、默认每页 10 | ✅ 合理（防护性常量） |
| 5 | `fragments/layout.html` | Bootstrap CDN 地址（`cdn.bootcdn.net`） | ✅ 合理：**全站只出现在一处**（改 CDN 只改 1 个文件）；且无外网时页面仍可用——可见样式都在 `static/css/fh.css` |

### 4.3 没有 TODO/FIXME 标记

全项目扫描 `TODO` / `FIXME` / `XXX` / `HACK`：**零处** ✅
（说明「临时做法」都写成了完整注释，而不是丢一个 TODO 走人 —— 这是好事）

---

## 5. 依赖与版本

**父 POM**：`spring-boot-starter-parent 3.3.5` ｜ **Java**：`21` ｜ **打包**：可执行 jar

| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-web` | 3.3.5 | Spring MVC + 内嵌 Tomcat |
| `spring-boot-starter-thymeleaf` | 3.3.5 | 服务端模板引擎 |
| `spring-boot-starter-data-jpa` | 3.3.5 | ORM（Hibernate 6.5.3） |
| `com.mysql:mysql-connector-j` | 8.3.0 | JDBC 驱动（scope=runtime） |
| `spring-boot-starter-security` | 3.3.5 | 认证 + 授权（**BCrypt 由它传递提供**） |
| `thymeleaf-extras-springsecurity6` | 3.3.5 | 模板里的 `sec:authorize` |
| `spring-boot-devtools` | 3.3.5 | 热重载（scope=runtime, optional） |
| `spring-boot-starter-test` | 3.3.5 | 测试（scope=test，**目前没用上**） |
| `org.projectlombok:lombok` | 1.18.34 | 只用于实体/DTO 的 getter/setter（**禁止 `@Data`**） |

> ⚠️ **刻意没有引入的依赖（会影响功能形态，写在明处）**：
> - **Apache POI** —— 导出实现为 **CSV（UTF-8 BOM，Excel 可直接打开）**，不是 `.xlsx`
> - **前端图表库（ECharts 等）** —— 日历图改成**服务端 CSS 格子**（`util/CalendarGrid`），CDN/JS 出问题也不会白屏

---

## 6. 数据模型

### 6.1 表清单与使用情况（数据库共 **20** 张）

> 建表来源：`sql/01-schema.sql` = **19 张** ＋ `sql/04-add-site-settings.sql` = 第 **20** 张 `site_settings`。

**已在用（13 张，有 Java 实体映射）**

```
users（用户，所有角色共用一张表）
  id / username🔑 / student_no🔑 / phone🔑 / password_hash / real_name
  role（枚举存字符串）/ logistics_duty / group_id（→ study_groups）
  status（枚举存字符串，含 PENDING）/ last_login_at / created_at / updated_at

student_profiles（成员档案，与 users 一对一）
  user_id（主键 + 外键 → users.id，CASCADE）
  grade / major_class / qq / wechat / email / join_date(DATE)
  languages / level（枚举）/ created_at / updated_at

study_groups（学习小组；运行期可增删改）
  id / name🔑 / description / leader_user_id（→ users，SET NULL；实体侧 leader 用 @ManyToOne）
  sort_order / created_at / updated_at

attendance_events / attendance_records（考勤活动 / 考勤记录，代打留痕 operator_id）
news / news_images
awards / award_students（含 is_public 公开意愿）/ award_images
upload_files（上传文件登记）
password_reset_requests（忘记密码申请：申请 + 审核，**不是重置**）
site_settings（站点级键值配置：setting_key 主键 / setting_value / updated_at）
```

**保留但暂未被 Java 用到（7 张）**

```
checkins（学习打卡记录）
  id / user_id（→ users，CASCADE）/ checkin_date / metric_type / metric_value / source / note / operator_id
  🔑 唯一约束（user_id, checkin_date, metric_type）
  📌 【一期不做界面】二期题库按 docs/10 的契约往这里写；**表必须保留**（契约依赖它）

comments（成员评语，**空的，功能已取消**）
chapters / questions / quiz_answers / oj_problems / oj_completions
  ↑ 题库 5 张：**已建好但还没被 Java 用到**（二期才实现；契约见 [10-题库API契约](10-题库API契约.md)）
```

> 🔑 = 唯一约束

### 6.2 实体映射的三个风格差异（**不是笔误，是刻意的**）

| 实体 | 主键 | 关联字段 | 为什么这样 |
|---|---|---|---|
| `User` | `@GeneratedValue(IDENTITY)` | `groupId` 用**裸 Long** | 最直白的映射，先打稳地基 |
| `StudentProfile` | **裸 Long（无 @GeneratedValue）** | 无 | 主键**就是**外键，档案跟着人走 |
| `StudyGroup` | `@GeneratedValue(IDENTITY)` | `leader` 用 `@ManyToOne`（**EAGER，刻意不要改 LAZY** —— 已关闭 OSIV） | 组列表要显示组长姓名，用裸 ID 就得再查一次 users |

---

## 7. 路由与页面清单

### 7.1 页面（**29 个**页面 + 2 个 fragment）

| 路径 | 模板 | 权限 | 说明 |
|---|---|---|---|
| `/` | index.html | 🌐 游客 | 首页：社团介绍 + 焦点图轮播 + 最新新闻 + 获奖公示 + 功能入口 + 建设进度 |
| `/about` | about.html | 🌐 游客 | 社团介绍 / 招新页（12 个区块） |
| `/awards` | awards.html | 🌐 游客 | ⭐ 获奖展示墙（只显示 `is_public=1`，**不展示证书照片**） |
| `/news` | news/list.html | 🌐 游客 | 新闻列表 |
| `/news/{id}` | news/detail.html | 🌐 游客 | 新闻详情 |
| `/login` | login.html | 🌐 游客 | 登录表单 |
| `/register` | register.html | 🌐 游客 | 自助注册 |
| `/forgot` | forgot.html | 🌐 游客 | 忘记密码申请 |
| `/403` | 403.html | 🌐 游客 | 越权提示 |
| `/error` | error/404.html、error/500.html | 🌐 游客 | 错误页 |
| `/me` | me/index.html | 🔑 已登录 | 我的主页：头像区 + ⭐ **签到日历** + 常用功能 |
| `/me/attendance` | me/attendance.html | 🔑 已登录 | 我的考勤 + 自助签到 + 签到日历 + 本周活动 |
| `/me/awards` | me/awards.html | 🔑 已登录 | 我的获奖 + **公开意愿开关**（只有本人能开） |
| `/me/group` | me/group.html | 🔑 已登录 | 我的小组（**组长专用**，入口在 `/me`，非组长看不到） |
| `/me/profile` | me/profile.html | 🔑 已登录 | 我的资料（只能改 QQ / 微信 / 邮箱） |
| `/me/password` | me/password.html | 🔑 已登录 | 改自己的密码 |
| `/admin` | admin/index.html | 🔒 社长层 | 后台总览（4 个 KPI + 各组人数 + 导出入口 + 快捷入口） |
| `/admin/students` | admin/students.html | 🔒 社长层 | 成员列表（搜索 + 状态筛选 + 按组筛选 + 分页 + 审核按钮） |
| `/admin/students/{id}` | admin/student-detail.html | 🔒 社长层 | 详情 + 编辑 + **账号管理（副社长看不到）** |
| `/admin/students/new` | admin/student-new.html | 🔒 社长层 | **手工补录成员**（一次建号 + 建档） |
| `/admin/groups` | admin/groups.html | 🔒 社长层 | 分组管理：建组 / 改组 / 定组长 / **加组员** / **删组** |
| `/admin/attendance` | admin/attendance.html | 🔒 社长层 | 考勤活动 + 代打卡 + 出勤率 + 导出 |
| `/admin/attendance/{id}` | admin/attendance-detail.html | 🔒 社长层 | 某次活动的考勤明细 |
| `/admin/news` | admin/news.html | 🔒 社长层 | 新闻管理（发布 / 置顶 / 删除） |
| `/admin/news/new`、`/admin/news/{id}/edit` | admin/news-form.html | 🔒 社长层 | Markdown 编辑器 + 封面裁剪 |
| `/admin/awards` | admin/awards.html | 🔒 社长层 | 获奖管理 |
| `/admin/awards/new`、`/admin/awards/{id}/edit` | admin/award-form.html | 🔒 社长层 | 获奖录入 / 编辑（团队奖一奖多人 + 每人公开意愿） |
| `/admin/settings` | admin/settings.html | 🔒 社长层 | 站点设置 / 首页背景图 |
| （公共片段，不是独立页面） | fragments/layout.html、fragments/icons.html | — | 公共外壳（head/nav/footer）与内联 SVG 图标 |

### 7.2 接口清单（**60+ 个映射**，出自 28 个 `@Controller`）

```
【公开 / 游客】
GET  /                        首页
GET  /ping                    健康检查，返回 pong
GET  /about                   社团介绍 / 招新页
GET  /news                    新闻列表
GET  /news/{id}               新闻详情
GET  /awards                  获奖展示墙
GET  /login                   登录页（POST /login 由 Spring Security 过滤器接管，不经过 Controller）
GET  /register                注册页
POST /register                提交注册（建 users + student_profiles，状态 = PENDING 待审核）
GET  /forgot                  忘记密码申请页
POST /forgot                  提交申请（存 password_reset_requests，PENDING）
GET  /403                     越权页（⚠️ 用 @RequestMapping，接受所有方法 —— 改回 @GetMapping 会变 405）
GET  /error                   错误页（内部转发）

【个人中心（登录即可）】
GET  /me                      我的主页（头像区 + 签到日历）
GET  /me/attendance           我的考勤 + 签到日历 + 本周活动
POST /me/attendance/{eventId} 自助签到（SELF）
GET  /me/awards               我的获奖
POST /me/awards/{linkId}/public  本人切换「是否对外公开」（只有本人能改）
GET  /me/group                我的小组（组长专用）
GET  /me/profile              我的资料页
POST /me/profile              保存 QQ / 微信 / 邮箱（手机号不在这里改）
GET  /me/password             改密码页
POST /me/password             提交改密码（校验旧密码）

【后台（hasAnyRole TEACHER / PRESIDENT / VICE_PRESIDENT）】
GET  /admin                                   后台总览
GET  /admin/students                          成员列表（参数 kw / status / groupId / page / size）
GET  /admin/students/{id}                     成员详情
POST /admin/students/{id}                     保存编辑（账号字段 + 档案字段）
GET  /admin/students/new                      手工补录成员的表单
POST /admin/students                          提交补录：**一次建号 + 建档**（新账号默认 ACTIVE 在读）
POST /admin/students/{id}/status              改账号状态（⚠️ 仅指导老师 + 正社长）
POST /admin/students/{id}/role                改角色（⚠️ 仅指导老师 + 正社长）
POST /admin/students/{id}/approve             审核通过（PENDING → ACTIVE）
POST /admin/students/{id}/reject              审核拒绝（删除该申请）
POST /admin/students/reset/{id}/approve       忘记密码申请：审核通过（此时才写回新密码哈希）
POST /admin/students/reset/{id}/reject        忘记密码申请：拒绝
GET  /admin/groups                            分组管理页
POST /admin/groups                            新建组
POST /admin/groups/{id}                       改组名 / 说明 / 排序
POST /admin/groups/{id}/leader                指定组长
POST /admin/groups/{id}/member                添加组员（若他在别的组会从原组移出）
POST /admin/groups/{id}/delete                删除组（组员自动变「未分组」）
GET  /admin/attendance                        考勤活动列表 + 出勤率
POST /admin/attendance/events                 新建考勤活动
GET  /admin/attendance/{id}                   活动考勤明细
POST /admin/attendance/{id}/mark              代打卡（记录 operator_id 留痕）
GET  /admin/attendance/export/weekly          导出本周考勤（**CSV**）
GET  /admin/attendance/export/all             导出全部考勤（**CSV**）
GET  /admin/news                              新闻管理列表
GET  /admin/news/new                          新建新闻页
POST /admin/news/new                          提交新建
GET  /admin/news/{id}/edit                    编辑新闻页
POST /admin/news/{id}/edit                    提交编辑
POST /admin/news/{id}/publish                 发布 / 下架
POST /admin/news/{id}/top                     置顶 / 取消置顶
POST /admin/news/{id}/delete                  删除
GET  /admin/awards                            获奖管理列表
GET  /admin/awards/new                        新建获奖页
POST /admin/awards/new                        提交新建
GET  /admin/awards/{id}/edit                  编辑获奖页
POST /admin/awards/{id}/edit                  提交编辑
POST /admin/awards/{id}/delete                删除
GET  /admin/settings                          站点设置页
POST /admin/settings/background               上传背景图并保存到 site_settings
POST /admin/settings/background/reset         恢复默认背景
POST /admin/upload                            通用上传（返回 JSON；需 CSRF token）
GET  /admin/export/students                   导出成员名单 CSV（🔒 不含手机号）
GET  /admin/export/awards                     导出获奖清单 CSV（含「同意公开人数 / 总人数」）
```

### 7.3 安全白名单（[SecurityConfig](../src/main/java/com/fountainhub/config/SecurityConfig.java)）

```
permitAll（游客可访问）：
  /  ·  /ping  ·  /about  ·  /news/**  ·  /awards
  /register  ·  /forgot
  /css/** /js/** /images/** /uploads/** /webjars/** /favicon.ico
  ← ⚠️ **`/uploads/**` 必须放行**：漏了它，**所有上传的图片在公开页面都是裂图**
    （游客拿到 302 跳登录；新闻封面 / 社团介绍页配图 / 首页背景图全受影响）
  /login  /logout  /error  /403

hasAnyRole("TEACHER","PRESIDENT","VICE_PRESIDENT")：
  /admin/**
  ⚠️ 这只是【粗粒度】的"能不能进后台"；「副社长不能改角色 / 禁用账号」由
     @PreAuthorize 在方法上做【细粒度】限制（AdminStudentAccountController）

authenticated（登录即可）：
  /me/**
  其余全部
```

---

## 8. 代码问题清单

> 按「**影响 × 修复成本**」排序。

### 8.1 重复代码

| # | 问题 | 现状 |
|---|---|---|
| D1 | 参数清洗重复 | ✅ **已解决**：收进 `util/FormUtils.blankToNull()` |
| D2 | 枚举解析重复 | ✅ **已解决**：收进 `util/FormUtils.parseEnum()` |
| D3 | 各页面重复写公共样式 | ✅ **已解决**：抽成 `templates/fragments/layout.html` |
| D4 | Bootstrap CDN 重复 | ✅ **已解决**：全站只出现在 `layout.html` 一处 |
| D5 | 取当前用户的写法重复 | ✅ **已解决**：统一到 `security/CurrentUserService` |
| D6 | 签到日历的计算逻辑 | ✅ **已解决（2026-10-04）**：收进 `service/AttendanceCalendarService`（`/me` 与 `/me/attendance` 共用）<br>⚠️ **残留一处刻意的重复**：日历那段 HTML 在两个模板里各有一份（因为两页的年份链接地址不同）——**改一处要一起改**，两个文件头部都有提示 |

### 8.2 大文件 / 职责过多

| # | 问题 | 现状 |
|---|---|---|
| B1 | 学员控制器职责过多 | ✅ **已解决**：拆成 `AdminStudentController` + `AdminStudentAccountController` |
| B2 | ProfileController 职责过多 | ✅ **已解决**：拆成 `ProfileController` + `PasswordController` |
| B3 | 注册参数多达 13 个 | ✅ **已解决**：改用 `dto/RegisterForm`，参数 13 → 1 |

### 8.3 命名与结构

| # | 问题 | 说明 |
|---|---|---|
| N1 | 实体名 `Level` 太泛 | 将来若有「难度级别」会撞名，建议 `StudentLevel` |
| N2 | `DbUserDetailsService` 在 security 包，其他服务类在 service 包 | `service/` 包**已存在**（3 个服务），所以现在的说法是「security 包是个特例」 |
| N3 | `UserSpecifications` 在 repository 包 | 合理，但将来查询多了要考虑 `repository/spec` |

### 8.4 错误处理缺失

| # | 问题 | 影响 |
|---|---|---|
| E1 | 无统一异常处理 | ✅ **已解决**：新增 `GlobalExceptionHandler` |
| E2 | 无自定义错误页 | ✅ **已解决**：`templates/error/404.html` + `500.html` |
| E3 | `IllegalStateException` 直接抛（「已登录却查不到用户」） | 属兜底，但会变成 500 白板页 |
| E4 | 唯一约束冲突靠「提前查重」避免 | 并发下仍可能撞库（概率极低） |

### 8.5 安全隐患

| # | 问题 | 严重度 | 说明 |
|---|---|---|---|
| ~~S1~~ | 开发测试账号的默认密码明文写在代码里 | ✅ **已解决（2026-10-04）** | 改为读**环境变量 `FH_INIT_PASSWORD`**；源码里**不再有任何明文密码**；不设变量则不建账号 |
| S4 | **没有登录失败次数限制 / 账号锁定** | 🟡 中 | 可被暴力猜密码。个人社团系统风险可接受，但要知道 |
| S5 | **公开注册页没有验证码** | 🟢 低 | 理论上能被机器人灌垃圾；唯一约束能挡住重复 |
| S6 | 会话没有显式超时配置 | 🟢 低 | 用框架默认（30 分钟） |
| S7 | 没有 CSP / X-Frame-Options 等安全响应头 | 🟢 低 | 框架默认已给了一部分 |
| S8 | 手机号**明文存数据库** | 🟢 低 | 一期可接受；二期若要合规可考虑加密列 |

**已经做对的（值得保持）** ✅：

- 密码 **BCrypt**，数据库里无明文；源码里也无明文密码
- 所有改数据的表单都用 `th:action` → **自动带 CSRF token**
- **不允许批量赋值**：注册/改资料都只显式接收允许字段
- **从会话取当前用户**，URL 里没有 id → 改不了别人
- 分页参数**消毒**（页码非负、size ≤ 50、非法枚举兜底）
- 数据库密码**不在 Git 里**
- 越权返回**我们自己的 403 页**，不是白板

### 8.6 其他

| # | 问题 | 说明 |
|---|---|---|
| O1 | 冗余依赖 | ✅ **已解决**：`spring-security-crypto` 已从 `pom.xml` 删除 |
| O2 | **`last_login_at` 从来没被写过** | 实体映射了、详情页显示了，但永远是空 |
| O3 | 白名单放行了、页面还没做 | ✅ **已解决**：`/about` 与 `/news/**` 都已实现 |
| O4 | 注释量很大（好事） | 注释占比高。**建议保持** —— 这是项目的教学定位决定的 |
| O5 | `.installer/` 里躺着临时文件 | 已被 gitignore，但占磁盘，可删 |

---

## 9. 不可动摇的约定（改动前必读）

1. **一次只做一个任务**（小步提交）
2. **改代码前先输出计划并获得确认**
3. **不新增依赖**（Lombok 是唯一例外，且**禁止 `@Data`**）
4. **不删除、不重命名文件**（除非明确批准）
5. **不自动 commit / push**；**不执行** `git reset --hard` / `push --force` 等
6. **不读取、不输出密码** —— 检查 `application*.yml` 前先过滤掉 password 行
7. **🔒 三条安全铁律**：
   - 密码一律 **BCrypt**，数据库里绝不明文；**源码里也不许有明文密码**
   - **从会话取当前用户**，绝不用 URL 里的 id
   - **只显式接收允许的字段**，绝不整体绑定（防批量赋值）
8. **🔒 手机号可见范围**：不得出现在**任何公开页面**；**管理员后台可见**；**导出的名单不含手机号**
9. **模板里的链接一律用 `@{...}`**，变量才用 `${...}` —— 混淆会 500（**已踩坑 5 次**）
10. **写完模板必跑**全项目扫描：以 `th:(action|href|src)="`${` 开头的就是错的
11. **改了 Java 常量必须重启**（模板缓存是关的，但常量编译在 JVM 里）
12. **模板表达式里，三元运算符必须整体写在 `${...}` 内部**（写在外面会被当纯文本原样输出）

---

## 10. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| **v2.0** | **2026-10-04** | 🔄 **对外可发布版（大改）**：① 清掉真实社团名、明文密码常量等敏感信息与**全部死链**（原文引用了未随仓库发布的文档）；② 按实测更新规模（**81 Java 文件 / 5,410 行 · 31 模板 · 29 Controller · 13 实体 + 10 枚举 · 14 仓库 · 3 服务 · 20 张表**）；③ ⭐ **按"学习功能下线"重写**：删掉 `Checkin` / `CheckinMetric` / `CheckinSource` / `CheckinRepository` 与 `/me/study` 的条目，`/me` 的说明由"绿色热力图"改为"签到日历"，并新增 `service/AttendanceCalendarService` 的口径收口说明（§8.1 D6）；④ `checkins` 表移入"保留但暂未被 Java 用到"，并写明**为什么必须保留**（二期题库契约依赖）；⑤ §4.1 删掉已解决的项、新增二期题库；⑥ §4.2 明确 `DataInitializer` 的密码已改为**环境变量**；⑦ §8.5 **S1 标记为已解决**（源码无明文密码）；⑧ §7 路由表补 **加组员 / 删组** 两条，删掉 `/me/study`；⑨ §9 约定新增两条踩坑教训（改 Java 常量要重启、Thymeleaf 三元必须写在 `${}` 内） |
| v1.7 | 2026-10-04 | ✅ T-045（手工补录成员）完成后同步：规模与页面数更新、补 `/admin/students/new` 路由 |
| v1.6 | 2026-10-04 | 🐞 修 `/uploads/**` 未放行（公开页上传图片全是裂图）。**已立纪律**：图片/下载类功能必须**真的取一次字节**看 `Content-Type` 与长度 |
| v1.5 | 2026-10-04 | ✅ T-042 接线（首页背景图）完成后同步 |
| v1.4 | 2026-10-04 | ✅ T-036（导出）完成后同步；新增 `util/CsvFile` 把 BOM / 中文文件名 / 转义收口到一处 |
| v1.3 | 2026-10-04 | 🧹 文档 ↔ 实际项目对齐（只改文档、不动代码） |
| v1.2 | 2026-10-02 | 同步 9 条重构 |
| v1.1 | 2026-10-02 | 同步 T-014（首页 + 导航栏） |
| v1.0 | 2026-10-02 | 首次全量盘点。只读生成，**未改动任何业务代码** |
