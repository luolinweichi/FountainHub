# FountainHub · 校园社团管理系统

> 一个**基于 Spring Boot 的社团内部协作与管理系统** —— 服务端渲染、零前端构建、单 jar 部署。
> 面向校园社团的日常运转：**成员与权限、考勤签到、新闻发布、获奖公示、站点配置**。

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3-005F0F)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey)

---

## 目录

- [一、这是什么](#一这是什么)
- [二、功能一览](#二功能一览)
- [三、技术栈](#三技术栈)
- [四、本地运行指南（Quick Start）](#四本地运行指南quick-start)
- [五、初始账号说明](#五初始账号说明)
- [六、管理员操作手册](#六管理员操作手册)
- [七、常见问题（FAQ）](#七常见问题faq)
- [八、改成你自己社团的](#八改成你自己社团的)
- [九、项目结构](#九项目结构)
- [十、已知边界](#十已知边界)
- [十一、这个项目是怎么做出来的](#十一个项目是怎么做出来的)
- [十二、许可](#十二许可)

---

## 一、这是什么

一套**给校园社团用的管理系统**，解决三件事：

| # | 解决什么 | 怎么做 |
|---|---|---|
| 1 | **人怎么管** | 成员自助注册 → 社长审核；角色分权（指导老师 / 正社长 / 副社长 / 组长 / 学员）；退社、毕业、禁用都有对应状态 |
| 2 | **活动怎么记** | 到场自助签到 + 签到日历；社长可代打卡、可导出考勤表 |
| 3 | **对外怎么讲** | 社团介绍页、新闻发布（Markdown 正文）、获奖公示墙；**文案全部可在后台改，不用改代码** |

**它不是**：不是一个通用 CMS，不是一个 OA 系统，也**不是**为多社团 / 多租户设计的 SaaS。
它就是"**一个社团、一台服务器、一个 jar**"这个规模的东西 —— 简单、看得懂、改得动。

> 🔒 **隐私设计（本项目的一条硬红线）**
> 学员手机号**只在数据库里**，任何页面都不展示；获奖信息对外公示**必须经本人同意**。
> 详见 [`docs/spec.md`](docs/spec.md) 第 3.3 节。

---

## 二、功能一览

### 账号与权限（RBAC）

| 能力 | 说明 |
|---|---|
| **三种登录方式** | 用户名 / 学号 / 手机号，**任意一种 + 密码**都能登录 |
| **自助注册 + 审核** | 游客提交注册申请 → 状态为「待审核（PENDING）」，**登不进去**；社长审核通过后才生效 |
| **五种角色** | 指导老师（全部权限）· 正社长 · 副社长（不能管账号）· **组长（派生出来的）** · 学员 |
| **组长是派生的** | 不往 `users.role` 里存，而是看 `study_groups.leader_user_id` —— 换组长只改一处，不会出现"两份数据不一致" |
| **五种账号状态** | 待审核 / 在读 / **退社（LEFT）** / 毕业 / 已禁用 |
| **退社仍可登录** | 能看公开页面和自己的个人主页，但**任何后台页面一律 403** |
| **首次登录强制改密** | 用管理员给的初始密码登录后，**必须先改成自己的密码**，否则哪儿都去不了 |

### 学员档案与分组

- 学员列表（关键词 / 状态 / 分组筛选 + 分页）、详情、编辑
- **管理员手工补录**：一次建账号 + 建档案（用于线下招新）
- **分组管理**：建组、指定组长；**组长只能看本组**的姓名与人数

### 考勤签到

- **到场自助签到**（成员自己点）+ **签到日历**（按"周几 × 周次"可视化）
- 后台代打卡、按周/全部导出 CSV

### 新闻发布

- 后台发布 / 编辑 / 发布 / 退回草稿 / 置顶 / 删除
- 正文用 **Markdown**（自带极简渲染器，零 CDN 依赖）
- 封面图上传（**浏览器端裁剪成 3:1 → 1200×400**，和首页轮播同尺寸）
- 「仅登录可见」开关 —— 草稿与内部新闻不会出现在公开页

### 获奖管理

- 录入获奖（级别 / 奖项 / 比赛 / 团队奖 / 证书多图）
- **本人公开意愿**：只有同意公开的获奖，才会出现在 `/awards` 展示墙和首页
- 导出获奖表 CSV

### 站点设置

- **首页背景图**：上传（自动裁剪）+ 实时效果预览 + 恢复默认
- **⭐ 全站文案可视化配置**：社团名称、口号、首页标题 / 副标题 / 按钮、社团介绍页**全部区块的标题与正文**
  —— 后台一个框一个框地填，**长文本支持 Markdown 预览**，不懂代码也能改

---

## 三、技术栈

| 层 | 选型 | 为什么 |
|---|---|---|
| 语言 / 运行时 | **Java 21** | 长期支持版 |
| 框架 | **Spring Boot 3.3.5** | Web + Security + Data JPA 一站配齐 |
| Web | **Spring MVC + Thymeleaf**（服务端渲染） | 不需要前后端分离，也不需要 Node / 构建工具 |
| 安全 | **Spring Security 6** | 表单登录 + 方法级权限（`@PreAuthorize`）+ CSRF |
| 持久化 | **Spring Data JPA / Hibernate**（`ddl-auto: validate`） | 表由手写 SQL 建，**Hibernate 只校验、绝不改表** |
| 数据库 | **MySQL 8.0+**（utf8mb4） | |
| 样式 | **Bootstrap 5**（只用栅格 + 弹窗，走 CDN） | 页面所有可见样式都是自己写的 `fh.css`；**CDN 挂了页面依然完整可用** |
| 前端脚本 | **原生 JS**，零框架零构建 | 全项目只有几个几十行的 `.js` 文件 |
| 构建 | **Maven** | `mvn clean package` 出一个可执行 fat jar |

> 💡 **"零构建"是刻意的**：`src/main/resources/static/` 下的 CSS / JS 直接就是产物，
> 没有 npm、没有打包步骤 —— 克隆下来就能编译，对维护者最友好。

---

## 四、本地运行指南（Quick Start）

### 1. 环境要求

| 需要 | 版本 | 说明 |
|---|---|---|
| **JDK** | **21** | `java -version` 应显示 21.x |
| **MySQL** | **8.0 或更高** | 需要能建库、能跑 `mysql` 命令行 |
| Maven | 3.6+ | 只有"自己编译"才需要；直接用 jar 则不需要 |

<details>
<summary>Ubuntu 上装 JDK 21 的一个坑（点开看）</summary>
Ubuntu **22.04** 的默认软件源里**没有** `openjdk-21-jdk`，`apt install` 会报"找不到软件包"。
要么用 Ubuntu 24.04+，要么加 **Adoptium** 官方源：

```bash
sudo apt install -y wget apt-transport-https gnupg
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-21-jdk
```
</details>

### 2. 建库

```bash
# ⚠️ 字符集必须是 utf8mb4，否则中文会乱码
mysql -u root -p -e "CREATE DATABASE fountainhub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 3. 导入建表脚本 —— **6 个文件，按顺序，一个都不能少** 🔴

```bash
mysql -u root -p fountainhub < sql/01-schema.sql                     # 建表（19 张）
mysql -u root -p fountainhub < sql/02-seed.sql                       # 示例数据（可跳过）
mysql -u root -p fountainhub < sql/03-add-password-reset-requests.sql
mysql -u root -p fountainhub < sql/04-add-site-settings.sql
mysql -u root -p fountainhub < sql/05-add-checkins.sql
mysql -u root -p fountainhub < sql/06-add-must-change-password.sql

# 核对：应该输出 20
mysql -u root -p fountainhub -e "SHOW TABLES;" | tail -n +2 | wc -l
```

| 脚本 | 作用 | 能跳过吗 |
|---|---|---|
| `01-schema.sql` | 建 19 张业务表（含外键、索引、唯一约束） | ❌ **必须** |
| `02-seed.sql` | 塞示例分组 + 3 条示例新闻 | ✅ 可以（不跑就是空页面） |
| `03-add-password-reset-requests.sql` | 给**老库**补"忘记密码申请"表 | ❌ **必须**（幂等，已存在会自动跳过） |
| `04-add-site-settings.sql` | 第 20 张表：站点配置（键值对） | ❌ **必须**（幂等） |
| `05-add-checkins.sql` | 给**老库**补回 `checkins` 表 | ❌ **必须**（幂等） |
| `06-add-must-change-password.sql` | `users` 加 `must_change_password` 列 | ❌ **必须**（幂等） |

> 🔴 **为什么 03~06 不能漏**：应用里配的是 **`ddl-auto: validate`** ——
> Hibernate **只校验表结构、绝不自动改表**。缺任何一张表 / 一列，**应用会直接启动失败**，并明确告诉你缺什么。
>
> ✅ 好消息：03~06 全都是**幂等**脚本（用 `IF NOT EXISTS` / `information_schema` 判断），
> **重复执行不会报错** —— 所以"照单全跑"永远是最省心的做法。

### 4. 配数据库密码（**不进 Git**）

项目约定：**密码只放在一个被 `.gitignore` 忽略的文件里**，`application.yml` 里没有密码行。

```bash
cp src/main/resources/application-local.yml.example  src/main/resources/application-local.yml
# 然后编辑 application-local.yml，把 password 换成你自己的
```

`application-local.yml` 的样子（**示例，请填自己的**）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/fountainhub?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password_here          # ← 改成你的 MySQL 密码
  jpa:
    hibernate:
      ddl-auto: validate                  # ⚠️ 千万别改成 update / create
    show-sql: false
```

> ⚠️ **`ddl-auto` 必须是 `validate`**。改成 `update` 会让 Hibernate 自动改表，
> 和你手写的 `sql/` 脚本产生分歧 —— 那是本项目刻意避免的。
>
> 🔒 若要填真实服务器地址，也请**只写在你本地这个文件里**（它不进 Git）。

### 5. 设初始账号密码（源码里没有任何明文密码）

```bash
export FH_INIT_PASSWORD='123456'        # Windows PowerShell: $env:FH_INIT_PASSWORD='123456'
```

> 只有**空库第一次启动**时才会用它建 3 个测试账号；不设这个变量就**不建账号**（代码里没有默认口令）。

### 6. 启动

**方式 A：Maven 直接跑（开发时用）**
```bash
mvn spring-boot:run
```

**方式 B：打成 jar 再跑（部署时用）**
```bash
mvn clean package -DskipTests
java -jar target/fountainhub-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:./application-local.yml
```

**方式 C：用一键脚本**
```bash
chmod +x start.sh && ./start.sh
```

启动成功后打开 <http://localhost:8080> ✅

---

## 五、初始账号说明

**空库首次启动**时，`DataInitializer` 会自动建 3 个测试账号（密码都是 `FH_INIT_PASSWORD` 的值）：

| 用户名 | 姓名 | 角色 | 能测什么 |
|---|---|---|---|
| `teacher` | 指导老师 | 指导老师 | **全部权限**（含改角色、禁用账号）|
| `president` | 社长 | 正社长 | 后台管理：学员 / 考勤 / 新闻 / 获奖 / 分组 / 站点设置 |
| `student` | 普通社员 | 学员 | 个人主页、签到、我的获奖、看新闻 |

⭐ `student` 还带**学号 `2025001`** 和**手机号 `13800000003`**（明显的假号）——
正好用来验证「**用户名 / 学号 / 手机号三种登录方式**」。

### 🔑 首次登录必须改密码

上面三个账号（以及以后**任何新建的账号**）首次登录后，会被**强制跳到「修改密码」页**，
**改完之前进不了其它任何页面** —— 这是为了防止"管理员设的初始密码被长期沿用"。

| 现象 | 说明 |
|---|---|
| 登录后被弹到 `/me/password`，顶部有橙色提示 | ✅ **正常**，不是 bug |
| 硬敲其它网址也被弹回来 | ✅ **正常**（服务端拦截，绕不过去）|
| 改完之后 `123456` 就登不上了 | ✅ **正常**（密码真的变了）|

> 💡 本地反复测试想跳过这一步：
> ```sql
> UPDATE users SET must_change_password = 0;                            -- 全部取消
> UPDATE users SET must_change_password = 1 WHERE username = 'teacher'; -- 只留一个测
> ```

---

## 六、管理员操作手册

> 这一节写给**不懂代码的社长 / 指导老师**。下面所有操作都在**浏览器里点**就能完成。

### 6.1 修改站点文案（不用改代码）

**入口**：登录 → 右上角「管理」→ 子导航「**站点设置**」，或直接访问 `/admin/settings`

页面上有 **3 个折叠面板**：

| 面板 | 里面有什么 |
|---|---|
| **全站通用** | **社团名称**（导航栏 / 页脚 / 登录框 / 介绍页大标题都用它）、**口号**（页脚与介绍页页首）|
| **首页文案** | 首页大标题、副标题、注册按钮文字、「社团简介」卡片正文 |
| **社团介绍页文案** | 每个区块一组：**标题 + 正文**（页首简介、我们做什么、我们学的方向、社团定位、日常活动、我们相信、怎么加入、常见问题、社训与号召）|

**怎么用**：

1. 点开要改的折叠面板 → 找到那一项 → 直接改文字
2. 长文本框旁边有「**预览 Markdown**」按钮 → 点一下就能看到排版效果
   （支持 `**加粗**`、`- 列表`、`# 标题`、`[链接](地址)`、`![图片](地址)`）
3. 页面**顶部和底部各有一个「💾 保存所有文案」**按钮 → 点任意一个保存（提交的是同一个表单）

**两条规则**：

| 规则 | 说明 |
|---|---|
| **留空 = 显示占位提示** | 某一项不填，页面上就显示 `【请填写：…】`，**不会变成空白**（这样你一眼能看出哪里还没写）|
| **「社团名称」和首页「大标题」是两个独立配置** | 想让它们一致就填成一样；想让首页标题更活泼，就分开写 |

### 6.2 换届交接（把社长权限交给下一届）

**入口**：管理 → **学员列表** → 找到那个人 → 点「编辑」

**步骤 A：给新社长提权**

1. 找到新一届社长 → **编辑**
2. **角色** 从「学员」改成「**正社长**」（副社长则选「副社长」）
3. 保存 → ⚠️ **让对方重新登录一次**（权限是登录时算出来的，旧会话还是老权限）

**步骤 B：把老社长 / 退社成员设为「已退社」**

1. 同样的编辑页 → **状态** 改成「**退社（LEFT）**」→ 保存
2. 效果：他**仍然能登录**（能回来看自己的记录），但**任何后台页面一律 403** ✅
3. 数据**不会丢**（考勤、获奖、打卡记录都还在）

> 💡 **为什么用"退社"而不是删账号**：删了账号，他的考勤 / 获奖 / 打卡记录会被外键**连带删除** ❌
> 用状态标记，数据永远留着 ✅

**状态怎么选：**

| 状态 | 能登录吗 | 能进后台吗 | 什么时候用 |
|---|---|---|---|
| 待审核 PENDING | ❌ | ❌ | 刚自助注册、还没审核 |
| 在读 ACTIVE | ✅ | 看角色 | 正常成员 |
| **退社 LEFT** | ✅ | 🔴 **一律不能** | 退社的人 |
| 毕业 GRADUATED | ✅ | 看角色 | 毕业生（能回来看记录）|
| 已禁用 DISABLED | ❌ | ❌ | 违规停用 |

**换组长**：管理 → **分组管理** → 编辑那个组 → 改「组长」→ 保存
（组长身份是派生的，**不需要动那个人的角色** ✅）

<details>
<summary><b>（可选）用 SQL 批量操作</b> —— 只有 DBA 级别的人才该碰数据库</summary>

```sql
-- 把某人提为正社长
UPDATE users SET role = 'PRESIDENT' WHERE username = 'zhangsan';

-- 把某人设为退社
UPDATE users SET status = 'LEFT' WHERE username = 'zhangsan';

-- 看全部账号现状（不含任何密码）
SELECT id, username, real_name, role, status FROM users ORDER BY id;
```
</details>

### 6.3 忘记密码怎么办（三层审核）

**入口**：登录页 → 「忘记密码」，或直接访问 `/forgot`

**普通成员（学员 / 组长）**：

1. 在 `/forgot` 填 **账号 + 想设置的新密码** → 提交
   （⚠️ **此时旧密码仍然有效** —— 新密码只存在申请表里，还没生效）
2. **指导老师 / 正社长 / 副社长** 任意一位 → 管理 → 学员列表 → 「⏳ 待审核的改密申请」→ 点「**通过**」
3. 通过的那一刻，新密码才真正生效 ✅

**社长 / 副社长自己忘记密码**：

- 他们也走 `/forgot` 提交申请，但**只有指导老师能审** ✅
  （列表里那一条，社长 / 副社长自己看是**没有按钮**的，会显示"只有指导老师能审"）

**指导老师自己忘记密码**：

- 页面上**谁都审不了** → **只能由维护人员直接改数据库**
- 所以 `/forgot` 页面对指导老师账号会直接提示"请联系系统维护人员"

<details>
<summary><b>维护人员操作：给指导老师重置密码（SQL 模板）</b></summary>

密码必须以 **BCrypt 哈希**存进 `password_hash`，**不能填明文**。

**第一步：算出 BCrypt 哈希**（二选一）

```bash
# 方法 A：用 htpasswd（apache2-utils 包，Ubuntu 一般自带）
sudo apt install -y apache2-utils
htpasswd -bnBC 10 "" 'your_new_password_here' | tr -d ':\n'
```

> ⚠️ 不同版本的 `htpasswd` 输出前缀可能是 `$2y$`。**先用一个测试账号验证一次**再动正式账号。

**第二步：写回数据库**

```sql
-- 情况 1：已经算好了哈希
UPDATE users
   SET password_hash        = '<把上一步的 BCrypt 哈希粘这里>',
       must_change_password = 1          -- ⭐ 让他登录后【强制】改成自己的密码
 WHERE username = 'teacher';

-- 情况 2（零额外工具）：从另一个"你知道密码"的账号复制哈希
UPDATE users
   SET password_hash        = (SELECT h FROM (SELECT password_hash AS h FROM users WHERE username = 'student') t),
       must_change_password = 1
 WHERE username = 'teacher';
```

**第三步**：让老师用那个临时密码登录 → 系统会**强制他立刻改成自己的密码** ✅
</details>

---

## 七、常见问题（FAQ）

> 这里每一条都是**开发 / 部署过程中真实踩过的**，不是设想出来的。

<details>
<summary><b>① 启动报 <code>Schema-validation: missing table/column</code>，应用起不来</b></summary>

**原因**：`sql/` 下的增量脚本没跑全（最常见是漏了 `03`~`06`）。
应用配的是 `ddl-auto: validate` —— **只校验、不改表**，缺东西就直接拒绝启动。

**解决**：把第四节里那 6 条命令**按顺序全跑一遍**（都是幂等的，重复跑不会出错），然后重启。
</details>

<details>
<summary><b>② <code>Access denied for user 'root'@'localhost' (using password: NO)</code></b></summary>

**原因**：Ubuntu 上装的 MySQL，`root` 默认用 **`auth_socket`** 插件 —— 只能 `sudo mysql` 免密进去，
**不能拿密码从 TCP 连**。而本应用走 JDBC 密码认证，所以要么给 root 设密码、要么建一个应用专用账号。

**解决（推荐：建专用账号，别用 root）**：

```sql
CREATE USER 'fh'@'localhost' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON fountainhub.* TO 'fh'@'localhost';
FLUSH PRIVILEGES;
```

然后把 `application-local.yml` 里的 `username` 改成 `fh`、`password` 填上面那个。
</details>

<details>
<summary><b>③ <code>E: Unable to locate package openjdk-21-jdk</code>（Ubuntu 22.04）</b></summary>

Ubuntu 22.04 的默认源里**没有** JDK 21。用 Ubuntu 24.04+，或按第四节里那段 Adoptium 源来装。
</details>

<details>
<summary><b>④ 中文全是乱码 / 页面出现 <code>???</code></b></summary>

**建库时字符集必须是 `utf8mb4`**：

```sql
CREATE DATABASE fountainhub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如果库已经建错了，只能重建（先备份数据）。
</details>

<details>
<summary><b>⑤ 上传图片失败，或上传后页面上是裂图</b></summary>

按顺序排查：

| 检查 | 怎么做 |
|---|---|
| **上传目录能写吗** | 应用要往 `fountainhub.upload.dir`（默认 `./uploads/`）写文件 —— 跑应用的用户必须对它有**写权限** |
| **路径是相对还是绝对** | 默认 `./uploads/` 是**相对于进程的工作目录**；用 systemd 启动时工作目录可能不是你预期的那个 → 建议在配置里写**绝对路径** |
| **`/uploads/**` 能访问吗** | 该路径已在安全配置里 `permitAll`；图裂了就先在浏览器直接打开图片地址看返回什么 |
| **文件大不大** | 单张上限 **5MB**、单请求 **10MB**（配在 `application.yml` 的 `spring.servlet.multipart`）|
| **后缀对不对** | 只接受 `jpg / jpeg / png / gif / webp`（**大小写不敏感**）|
</details>

<details>
<summary><b>⑥ 上传的明明是 .png，却报「只允许上传图片（jpg / jpeg / png / gif / webp）」</b></summary>

这是一个**真实踩过的坑**（已修复，留给改上传代码的人参考）：

后端是按**文件后缀**查白名单的。而"浏览器端裁剪"产出的 `canvas.toBlob()` 结果是一个**裸 Blob** ——
浏览器给它的文件名是 `"blob"`，**没有后缀** ❌ 于是白名单拒掉，可用户传的明明就是一张 png，提示完全看不懂。

**修法（两道防线，都留着了）**：

1. 前端裁剪完成后，**统一包成带后缀的 `File`**（`new File([blob], "cropped.png")`）
2. 后端**兜底**：拿不到后缀时看浏览器给的 `Content-Type`（`image/png` → `png`）——
   但**最终仍然只允许白名单里的那几种后缀**，落盘名仍然是随机 UUID，不引入新风险

> 📌 **教训**：所有"前端产出的二进制"都要**显式带上文件名 / 后缀**，别指望浏览器替你补。
</details>

<details>
<summary><b>⑦ 加了"登录弹窗"后，点登录整页变黑、弹窗点不动也输不了字</b></summary>

**原因**：弹窗被放在了 `<nav>` 里面，而导航栏是 `position: sticky; z-index: 1030` ——
**sticky + z-index 会让它自己形成一个"层叠上下文"**，于是弹窗（Bootstrap 的 `.modal` 是 1055）
只能在导航栏内部比大小，相对整个页面它只有 **1030** → **被 1050 的黑色遮罩盖住** ❌

**修法**：把弹窗移到**不形成层叠上下文**的容器里（本项目放在页脚 fragment 里）。

> 📌 **教训**：`position: sticky / fixed` + `z-index`、`transform`、`filter` 都会创建层叠上下文；
> 弹窗这类"要盖住全站"的元素，**祖先里不能有它们**。
</details>

<details>
<summary><b>⑧ 在后台改了文案，页面上没变化</b></summary>

| 检查 | 说明 |
|---|---|
| 点保存了吗 | 顶部 / 底部各有一个「保存所有文案」，**改完必须点一个** |
| 是不是"留空"了 | 留空 = 显示 `【请填写：…】` 占位提示，不是显示你上次填的 |
| 刷新页面了吗 | 文案是服务端渲染的，**刷新浏览器**即可（不需要重启应用）|
| 改的是这个键吗 | 同一段文字，首页和介绍页可能是**两个独立的配置项**（比如"社团名称"和首页"大标题"）|
</details>

<details>
<summary><b>⑨ 首次登录被强制改密，改完 <code>123456</code> 就登不上了</b></summary>

**这是设计如此** ✅ —— 改密真的生效了。
想反复测试"首次登录"流程，用这条把标记改回去（用你当前的新密码登录即可）：

```sql
UPDATE users SET must_change_password = 1 WHERE username IN ('teacher','president','student');
```
</details>

<details>
<summary><b>⑩ Ubuntu 上 <code>apt update</code> 报签名 / 时间相关错误</b></summary>

**这是一条通用的 Linux 注意事项**（不一定只发生在本项目上）：
虚拟机的系统时间如果**漂移得太多**（挂起恢复、宿主机休眠后很常见），
`apt` 会因为证书"还没生效 / 已过期"而拒绝源。

```bash
date                                              # 先看时间和真实时间差多少
sudo timedatectl set-ntp true                     # 打开自动校时
sudo systemctl restart systemd-timesyncd
```

时间对了再 `sudo apt update` 通常就好了。
</details>

---

## 八、改成你自己社团的

> 💡 **最快的路径**：启动后直接去后台 **站点设置** 里改 —— 大部分文案都能可视化地改 ✅
> 只有下面这几处"代码级"的东西需要动源码。

| # | 改什么 | 在哪 |
|---|---|---|
| 1 | **社团名称 / 口号** | ⭐ **不用改代码**：后台 → 站点设置 →「全站通用」|
| 2 | **首页与介绍页的全部文案** | ⭐ **不用改代码**：后台 → 站点设置 |
| 3 | **系统名**（导航栏左边的 `FountainHub`）| `config/GlobalModelAdvice.java` 的 `SYSTEM_NAME` |
| 4 | 首页背景图 | 后台 → 站点设置 → 首页背景图 |
| 5 | 数据库密码 | `src/main/resources/application-local.yml`（**已被 .gitignore 忽略，不会进 Git**）|

---

## 九、项目结构

```
FountainHub/
├─ README.md                 ← 你正在看的这份
├─ LICENSE                   ← Apache-2.0
├─ NOTICE                    ← 版权与第三方说明
├─ pom.xml                   ← Maven 配置（Java 21 / Spring Boot 3.3.5）
├─ docs/                     ← 项目文档（需求、设计、部署、开发日志…）
│   ├─ 00-快速开始.md          ← 更详细的步骤与报错排查
│   ├─ 01-需求规格说明书.md
│   ├─ 04-部署方案.md          ← 生产部署（systemd + Nginx）
│   ├─ 05-数据库设计说明书.md
│   ├─ 08-视觉与品牌规范.md
│   ├─ 09-开发进度日志.md      ← ⭐ 开发全过程日志（含所有踩坑记录）
│   ├─ spec.md                ← 编码契约
│   ├─ plan.md                ← 任务总表
│   ├─ CODE_MAP.md · TEST_PLAN.md
│   └─ tasks/                 ← 一个任务一份任务书
├─ sql/                      ← 建库脚本（01 建表 → 02 示例数据 → 03~06 增量，按序全跑）
├─ src/main/
│   ├─ java/com/fountainhub/
│   │   ├─ config/           ← 安全配置、全局模型、Web 配置
│   │   ├─ controller/       ← 页面与接口
│   │   ├─ entity/           ← 实体 + 枚举（含全站文案键注册表 SiteTextKeys）
│   │   ├─ repository/       ← Spring Data 仓库
│   │   ├─ security/         ← 登录、权限、强制改密拦截器
│   │   ├─ service/          ← 业务逻辑
│   │   └─ util/
│   └─ resources/
│       ├─ templates/        ← Thymeleaf 页面（fragments/ 是公共外壳）
│       ├─ static/           ← CSS / JS（零构建，直接就是产物）
│       └─ application.yml   ← 主配置（**没有密码行**）
└─ tools/                    ← 小工具
```

---

## 十、已知边界

**明确不做**（不是"没做完"，是**刻意不做**）：

| 不做 | 为什么 |
|---|---|
| 多社团 / 多租户 | 定位就是"一个社团一套"，加进去复杂度翻几倍 |
| 前后端分离 / SPA | 服务端渲染已经够用，且**不需要 Node 构建链** |
| 富文本编辑器 | 正文用 Markdown，自己写了 40 行渲染器，**零 CDN 依赖** |
| 单元测试（一期）| 一期以**手动验证 + 验收清单**为准；`src/test` 目前是空的 |
| 在线题库 / 教学视频 | 预留了表结构，但页面功能属二期 |

---

## 十一、这个项目是怎么做出来的

这个仓库里**最特别的东西不是代码，是 `docs/`**。

项目从第一天就按 **SDD（规格驱动开发）** 走：
**Spec → Plan → Tasks → Implement → Verify → Review → Log → Commit**。

- 每个功能**先写任务书**（`docs/tasks/`），批准了才动代码；
- **每一步都记日志**（`docs/09-开发进度日志.md`，4000+ 行）——
  记的不只是"做了什么"，还有**踩了什么坑、为什么这么决定、验证的真实输出**；
- 代码注释里写的是**"为什么这么写"**，而不是"这行在干什么"。

所以想了解这个项目：**先读 `docs/09-开发进度日志.md`** —— 它按时间倒序，最新的在最上面。

---

## 十二、许可

**Apache License 2.0** —— 见 [LICENSE](LICENSE)。

```
Copyright 2026 luolinweichi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

你需要遵守的：

- 📄 保留版权声明，并附一份 [LICENSE](LICENSE) 副本
- 📝 **你改过的文件**要标注出来（这是 Apache-2.0 比 MIT 严格的一点）
- ⚖️ 附带专利授权 —— 这正是它比 MIT 更适合"想被放心使用"的项目的原因

> 本仓库**不含任何第三方代码**；所用依赖（Spring Boot / Thymeleaf / MySQL 驱动 / Lombok 等）
> 各自遵循其原有协议。
