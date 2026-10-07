# task-023 · `News` + `NewsImage` 实体 + 仓库（M9 地基）

| 项 | 值 |
|---|---|
| 任务编号 | **T-023** |
| 任务名 | 新闻实体与仓库（只打地基，不做页面） |
| 对应 | **M9 新闻与公告**（第一个任务） |
| 状态 | ✅ **已完成**（2026-10-03） |

---

## 1. 任务理解

把 `news` + `news_images` 两张表映射成 Java 实体 + 建仓库。

### 三个关键字段（M9 后面三个任务全靠它们）

| 字段 | 值 | 后面谁用 |
|---|---|---|
| `status` | `DRAFT` 草稿 / `PUBLISHED` 已发布 | **T-026**：需求明确「**草稿状态的新闻游客访问不到**」→ 公开页必须按它过滤 |
| `isTop` | 置顶 | **T-026** 列表里置顶排最前 |
| `isInternal` | **仅登录可见** | **T-026** 也要按它过滤（内部通知不对外） |

## 2. 非目标

- ❌ 后台新闻管理（**T-024**）
- ❌ 图片上传（**T-025**）
- ❌ 对外公开页（**T-026**）
- ❌ **不改数据库**

## 3. 修改的文件

| 文件 | 动作 |
|---|---|
| `entity/NewsCategory.java` | **新建**（ACTIVITY / NOTICE / CONTEST / TECH） |
| `entity/NewsStatus.java` | **新建**（DRAFT / PUBLISHED） |
| `entity/News.java` | **新建** |
| `entity/NewsImage.java` | **新建** |
| `repository/NewsRepository.java` | **新建** |
| `repository/NewsImageRepository.java` | **新建** |
| 任务书 + plan + docs/09 | 更新 |

> ✅ **没有碰任何已有代码**

## 4. 关键设计

### 4.1 ⚠️ 正文 `LONGTEXT` 的映射 —— **本任务踩的最大的坑**

**第一版我用 `@Lob`，结果应用直接起不来** ❌：

```
Schema-validation: wrong column type encountered in column [content] in table [news];
  found    [longtext (Types#LONGVARCHAR)]
  expecting [tinytext (Types#CLOB)]
```

**原因**：`@Lob String` 会让 Hibernate 以为这一列是 CLOB，
而 MySQL 的 `LONGTEXT` 在 JDBC 里是 `LONGVARCHAR`，两边对不上。

**正确写法**（已改）：
```java
@Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
private String content;
```

> 💡 **这正是 `ddl-auto: validate` 的价值** —— 它把「映射写错」从
> 「运行时才发现」提前到了「**启动就失败**」✅

### 4.2 为什么配图单独一张表

一张新闻可能有 N 张图、还要能排序。用逗号拼字符串的话，
删中间一张、调顺序都得解析字符串。单独一张表 + `sort_order` 干净得多 ✅
（`news_images.news_id` 上有外键 CASCADE，删新闻配图自动跟着删 ✅）

### 4.3 仓库只加马上要用的 3 个方法

```java
List<News> findByStatusOrderByIsTopDescPublishedAtDesc(NewsStatus status);  // T-026 公开列表
List<News> findAllByOrderByIsTopDescCreatedAtDesc();                        // T-024 后台列表
List<NewsImage> findByNewsIdOrderBySortOrderAsc(Long newsId);               // T-025 / T-026
```

> ⚠️ `findByStatus...` **只过滤了 status** —— 「仅登录可见」那条要由 Controller 按
> 「游客还是登录用户」判断，放在 Controller 里更清楚 ✅

## 5. 验证命令

```cmd
cd /d D:\Project\FountainHub
mvn -B -ntp compile
mvn -o -B -ntp spring-boot:run
```

## 6. 完成记录（真实结果）

**完成日期**：2026-10-03

### 验证结果

| # | 项目 | 结果 |
|---|---|---|
| 1 | `mvn compile` | **BUILD SUCCESS** ✅ |
| 2 | 正常启动 | **启动成功** ✅；**`Schema-validation` 无报错** → `News` / `NewsImage` 与表**逐列吻合**（含 LONGTEXT）✅ |
| 3 | **负向测试**：把 `is_top` 改成 `is_top_typo` | **启动【失败】** ✅ + 报 `Schema-validation` ✅ + 沙箱无干扰（结论可信）✅ |
| 4 | 还原后重启 | 启动成功 ✅、无报错 ✅、**还原文件逐字节一致** ✅ |
| 5 | 日志 | **ERROR 0 条** ✅ |

### ⚠️ 中途的一次失败（值得记录）

第一次验证时 **验证 1 和验证 3 都失败了** —— 说明**我的映射真的写错了**。
（当时报错信息被截断，只能看到开头；我**去把完整报错抓出来**才定位到 `@Lob` 的问题 ✅）

> 👉 **教训：报错被截断时，一定要把完整的那一行捞出来，不要靠猜。**

### 验证边界（如实说明）

本任务**没有页面**，两张表也都是**空的** → **无法端到端验证「真的读出了数据」**。
能验证的是「**映射和表结构完全对得上**」—— 这正是本任务的主要风险 ✅
真正第一次用到是在 **T-024**（后台新闻管理）✅

## 7. 下一步

**T-024** 后台新闻管理：列表 / 新建 / 编辑 / 删除 / **草稿·发布** / **置顶** / 分类 ✅
