# task-025 · 图片上传（封面图 + 正文插图）

| 项 | 值 |
|---|---|
| 任务编号 | **T-025** |
| 接口 | `POST /admin/upload`；图片通过 `/uploads/**` 访问 |
| 对应 | **M9 新闻与公告** |
| 状态 | ✅ **已完成**（2026-10-03） |

---

## 1. 任务理解

- **封面图** → 上传后存 `news.cover_image`，列表和详情都能看到
- **正文插图** → 上传后往 Markdown 里插 `![图片](/uploads/xxx.png)`，同时登记到 `news_images`

## 2. 🔒 安全设计（本任务的重点）

`FileStorageService` 是全项目**唯一**把「用户传来的东西」写到磁盘的地方，所以规矩最严：

| # | 规矩 | 为什么 |
|---|---|---|
| 1 | **后缀白名单** jpg/jpeg/png/gif/webp | 否则传个 `.jsp` / `.html` 就可能被执行 |
| 2 | **落盘文件名一律随机 UUID** | 用户传 `../../etc/passwd` → **路径穿越**；传 `a.png` → 覆盖别人的图。随机名**两个问题一起解决** |
| 3 | 原始文件名**只存数据库**（`original_name`），不参与拼路径 | 同上 |
| 4 | 按年月分子目录（`2026/10/xxx.png`） | 图多了不至于一个目录塞几万张 |
| 5 | 大小上限 5MB（`application.yml`） | 防止有人传 500MB 把磁盘塞满 |
| 6 | 上传要**登录 + 社长层** | 不能让人随便往服务器写文件 |

**额外一道保险**：`store()` 里落盘前还会再确认一次
「解析后的绝对路径**必须**在上传目录里面」，双保险 ✅

## 3. 非目标

- ❌ 对外公开页（**T-026**）
- ❌ 视频上传（**二期**）
- ❌ 图片裁剪 / 压缩 / 缩略图
- ❌ **不改数据库**

## 4. ⚠️ 本次改了配置（AGENTS.md §7 要求先问，已获同意）

| 文件 | 改动 |
|---|---|
| `application.yml` | 加上传大小上限、`fountainhub.upload.dir` |
| `.gitignore` | 忽略 `uploads/`（上传的图是运行时数据，不该进 Git） |

## 5. 修改的文件

| 文件 | 动作 |
|---|---|
| `entity/UploadFile.java` | **新建** |
| `repository/UploadFileRepository.java` | **新建** |
| `service/FileStorageService.java` | **新建**（白名单 + 随机名 + 落盘 + 登记） |
| `controller/UploadController.java` | **新建**（`POST /admin/upload`，返回 JSON） |
| **`config/WebMvcConfig.java`** | **新建**（把 `/uploads/**` 映射到**绝对路径**） |
| `controller/AdminNewsController.java` | 改：接收封面 + `syncInlineImages()` 登记正文插图 |
| `templates/admin/news-form.html` | 改：封面上传 + 正文「插入图片」按钮（fetch + CSRF） |
| `templates/admin/news.html` | 改：列表加**封面列** |
| `controller/GlobalExceptionHandler.java` | 改：**超大文件给 JSON 人话提示** |
| `application.yml` / `.gitignore` | 改（见上） |
| 任务书 + plan + docs/09 | 更新 |

## 6. ⚠️ 本次踩的两个坑（都真发生了）

### 坑 1：改 `application.yml` 时**把 YAML 结构插坏了** → 应用起不来

```
Failed to configure a DataSource: 'url' attribute is not specified
```
我把新配置**插在了 `spring.datasource` 中间**，导致 `datasource` / `jpa` 从 `spring:` 下面掉了出来 ❌
**修法**：把整个文件**重写一遍**，顺序理顺 ✅

> 👉 **教训：改 YAML 不要「在某一行前面插一段」—— 要整块重写、并确认层级没被打乱。**

### 坑 2：图片上传成功，但 `/uploads/xxx.png` **404**

第一版我在 `application.yml` 里配 `static-locations: ...,file:./uploads/` ❌
**原因**：`./` 是「相对于进程的工作目录」，在不同启动方式（IDEA / mvn / jar）下**指哪里说不准**。
**修法**：新建 `WebMvcConfig`，**先把目录解析成绝对路径**再挂上去 ✅

> 👉 **教训：涉及文件路径的配置，别用相对路径碰运气，解析成绝对路径最稳。**

## 7. 完成记录（真实结果）

**完成日期**：2026-10-03

### 主体验证：**26 通过 / 2 失败** → 修好后复验 **5 通过 / 0 失败**（日志 ERROR 0 条）

| 组 | 结果 |
|---|---|
| 1 ★ 上传 | 成功 ✅；URL 形如 `/uploads/2026/10/<32位uuid>.png` ✅；**通过 URL 能拿到图（HTTP 200，70 字节）** ✅ |
| 2 🔒 白名单 | `.txt` / `.jsp` / `.html` / 无后缀 → **全部拒绝**，提示「只允许上传图片…」✅ |
| 3 🔒 路径穿越 | 传 `../../../evil.png` → **仍是随机 UUID、URL 里没有 `..`** ✅ |
| 4 🔒 超大文件 | 6MB → **被拒**；返回 **JSON 人话**「图片太大了（单张不能超过 5MB）…」✅（不是 HTML 错误页） |
| 5 ★ 封面图 | 新建带封面 ✅；**列表里出现封面 URL** ✅；**渲染成 `<img class="fh-coverthumb">`** ✅ |
| 6 🔒 权限 | 组长/学员上传 → **403** ✅ |
| 7 回归 | `/ping` ✅、4 个公开页 ✅、7 个老师页 ✅、404 仍是 404 ✅ |
| 日志 | ERROR **0** 条 ✅ |

### ⚠️ 那 2 条「失败」的真相

第一轮跑的时候，超大文件返回的是 **HTTP 200 + 一个内容为 `null` 的错误页** ——
因为 `error/500.html` **没有用任何变量**，我塞进去的 `message` 就丢了 ❌

**修法**：改成**直接返回 JSON**，正好和上传接口的约定一致，
页面上的 JS 本来就会把 `message` 显示出来 ✅

**另外也印证了一件事**：`uploads/2026/10/xxx.png` **确实落盘了**（我用 `Get-ChildItem` 看过，70 字节）
—— 说明 404 **纯粹是静态映射的问题**，不是写入失败 ✅

## 8. 下一步

**T-026** 对外公开页（`/news`、`/news/{id}`）—— **草稿游客看不到** ✅
