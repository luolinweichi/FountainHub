package com.fountainhub.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「全站文案」的配置键清单 —— **唯一来源**（2026-10-07 新增）。
 *
 * ===========================================================================
 * 【这个类是干什么的】
 *   首页和社团介绍页上所有"管理员应该能改"的文字，都在这里登记一条：
 *     键名 / 归哪个面板 / 归哪个折叠子项 / 中文名 / 是否长文本 / 是否支持 Markdown / 空值时的占位符
 *
 * 【为什么要有这么一张表，而不是直接把键名散在代码里】
 *   ① 键名只出现一次，改键名不会漏掉某处；
 *   ② **后台表单可以循环生成** —— 不用在 settings.html 里手写 40 多个输入框
 *      （手写就一定会漏、会拼错 name，而且加一项要改好几处）；
 *   ③ 占位符集中管理：配置为空时页面上显示什么，一眼能看全；
 *   ④ 保存时可以做**白名单校验** —— 只允许写这里登记过的键（见 SiteSettingService）。
 *
 * 【怎么加一项新文案】
 *   在下面 ALL 里加一行，然后：① 模板里 `${siteText['你的键']}` ② 后台自动就出现这个字段 ✅
 *
 * 【⚠️ 为什么 Item 是普通类而不是 record】
 *   Thymeleaf 用的是 SpEL 取属性，它对 record 的 `key()` 访问器支持不可靠；
 *   写成带 getter 的普通类，模板里 `${item.key}` 就一定是稳的。
 * ===========================================================================
 */
public final class SiteTextKeys {

    /** 后台大面板的名字（按这个顺序从上到下显示） */
    public static final String GROUP_SITE = "全站通用";
    public static final String GROUP_HOME = "首页文案";
    public static final String GROUP_ABOUT = "社团介绍页文案";

    /**
     * 🔴 社团名称 —— **全站的唯一来源**（导航栏 / 页脚 / 登录弹窗 / 首页大标题 / 介绍页大标题…）。
     * 【为什么它必须走配置】原来它是 GlobalModelAdvice 里的一个硬编码常量，
     *   导致"社团名到底显示什么"在代码里，管理员改不了（2026-10-07 项目负责人要求改掉）。
     */
    public static final String KEY_CLUB_NAME = "site.clubName";

    /** 社团口号 / 社训（页脚 + 介绍页页首）。⚠️ 它的占位符故意是【空串】——
     *  没配时页面上就是空的（原来就是这样），不会冒出一句「【请填写…】」破坏排版。 */
    public static final String KEY_CLUB_SLOGAN = "site.clubSlogan";

    /** 一个可配置的文案项 */
    public static final class Item {

        private final String key;
        private final String group;
        private final String block;
        private final String label;
        private final boolean multiline;
        private final boolean markdown;
        private final String placeholder;

        public Item(String key, String group, String block, String label,
                    boolean multiline, boolean markdown, String placeholder) {
            this.key = key;
            this.group = group;
            this.block = block;
            this.label = label;
            this.multiline = multiline;
            this.markdown = markdown;
            this.placeholder = placeholder;
        }

        /** 存进 site_settings.setting_key 的键名 */
        public String getKey() { return key; }
        /** 归到哪个大面板（首页文案 / 社团介绍页文案） */
        public String getGroup() { return group; }
        /** 归到哪个折叠子项（如「社团定位」） */
        public String getBlock() { return block; }
        /** 界面上显示的中文名 */
        public String getLabel() { return label; }
        /** true → 用 textarea；false → 用普通 input */
        public boolean isMultiline() { return multiline; }
        /** true → 给一个「预览 Markdown」按钮 */
        public boolean isMarkdown() { return markdown; }
        /** 配置为空时，页面上显示这句话（而不是空白） */
        public String getPlaceholder() { return placeholder; }
    }

    /** 值里面用「一行一个」表示多个条目的键（这两个都是"标签列表"） */
    public static final String KEY_VALUES_TAGS = "about.values.tags";
    public static final String KEY_HERO_TAGS = "about.hero.tags";

    private static final String HOME = GROUP_HOME;
    private static final String ABOUT = GROUP_ABOUT;

    /** ⭐ 全部可配置文案 —— 加一项就在这里加一行 */
    public static final List<Item> ALL = List.of(

            // ==================== 全站通用（2 项）====================
            new Item(KEY_CLUB_NAME, GROUP_SITE, "社团名称", "社团名称（导航栏 / 页脚 / 登录框 / 介绍页大标题都用它）",
                    false, false, "【请填写社团名称】"),
            new Item(KEY_CLUB_SLOGAN, GROUP_SITE, "社团口号", "社团口号 / 社训（页脚与介绍页页首；留空则不显示）",
                    false, false, ""),

            // ==================== 首页（4 项）====================
            new Item("home.hero.title", HOME, "首页文案", "大标题", false, false,
                    "【请填写社团名称】"),
            new Item("home.hero.subtitle", HOME, "首页文案", "副标题（大标题下面那句）", true, false,
                    "【请填写：一句话介绍你们社团做什么，2 行以内。】"),
            new Item("home.hero.buttonText", HOME, "首页文案", "注册按钮文字", false, false,
                    "注册加入"),
            new Item("home.about.content", HOME, "首页文案", "「社团简介」卡片正文", true, false,
                    "【请填写：首页左侧「社团简介」卡片的正文。】"),

            new Item(KEY_HERO_TAGS, ABOUT, "页首简介", "页首标签（**一行一个**）", true, false,
                    "【标签 1】"),

            // ==================== 社团介绍页 · 页首（2 项）====================
            new Item("about.intro", ABOUT, "页首简介", "页首那段定位文字", true, true,
                    "【请填写：一段 1~2 句话的社团定位 —— 你们面向谁、平时做什么。】"),

            // ==================== 社团介绍页 · 我们做什么（7 项）====================
            new Item("about.what.title", ABOUT, "我们做什么", "区块标题", false, false, "我们做什么"),
            new Item("about.what.item1.title", ABOUT, "我们做什么", "活动 1 · 标题", false, false, "【活动 1】"),
            new Item("about.what.item1.body", ABOUT, "我们做什么", "活动 1 · 正文", true, true,
                    "【请填写：这项活动做什么、什么时候开展。】"),
            new Item("about.what.item2.title", ABOUT, "我们做什么", "活动 2 · 标题", false, false, "【活动 2】"),
            new Item("about.what.item2.body", ABOUT, "我们做什么", "活动 2 · 正文", true, true,
                    "【请填写：这项活动做什么、什么时候开展。】"),
            new Item("about.what.item3.title", ABOUT, "我们做什么", "活动 3 · 标题", false, false, "【活动 3】"),
            new Item("about.what.item3.body", ABOUT, "我们做什么", "活动 3 · 正文", true, true,
                    "【请填写：这项活动做什么、什么时候开展。】"),

            // ==================== 社团介绍页 · 我们学的方向（11 项）====================
            new Item("about.direction.title", ABOUT, "我们学的方向", "区块标题", false, false, "我们学的方向"),
            new Item("about.direction.sub", ABOUT, "我们学的方向", "区块说明", false, false,
                    "【请填写：一句话说明怎么选方向。】"),
            new Item("about.direction.item1.title", ABOUT, "我们学的方向", "方向 1 · 标题", false, false, "【方向 1】"),
            new Item("about.direction.item1.body", ABOUT, "我们学的方向", "方向 1 · 说明", false, false, "【一句话说明】"),
            new Item("about.direction.item2.title", ABOUT, "我们学的方向", "方向 2 · 标题", false, false, "【方向 2】"),
            new Item("about.direction.item2.body", ABOUT, "我们学的方向", "方向 2 · 说明", false, false, "【一句话说明】"),
            new Item("about.direction.item3.title", ABOUT, "我们学的方向", "方向 3 · 标题", false, false, "【方向 3】"),
            new Item("about.direction.item3.body", ABOUT, "我们学的方向", "方向 3 · 说明", false, false, "【一句话说明】"),
            new Item("about.direction.item4.title", ABOUT, "我们学的方向", "方向 4 · 标题", false, false, "【方向 4】"),
            new Item("about.direction.item4.body", ABOUT, "我们学的方向", "方向 4 · 说明", false, false, "【一句话说明】"),
            new Item("about.direction.note", ABOUT, "我们学的方向", "区块备注", true, true,
                    "【请填写：这些方向的排期，以及成员在哪看通知。】"),

            // ==================== 社团介绍页 · 社团定位（2 项，正文支持 Markdown）====================
            new Item("about.mission.title", ABOUT, "社团定位", "区块标题", false, false, "社团定位"),
            new Item("about.mission.content", ABOUT, "社团定位", "正文（支持 Markdown）", true, true,
                    "【请填写：你们的理念 —— 想让成员在这里得到什么。】"),

            // ==================== 社团介绍页 · 日常活动（2 项，正文支持 Markdown）====================
            new Item("about.daily.title", ABOUT, "日常活动", "区块标题", false, false, "日常活动"),
            new Item("about.daily.content", ABOUT, "日常活动", "正文（支持 Markdown）", true, true,
                    "【请填写：例行活动多久一次、周几、一般在哪、做什么；以及出勤要求。】"),

            // ==================== 社团介绍页 · 我们相信（3 项）====================
            new Item("about.values.title", ABOUT, "我们相信", "区块标题", false, false, "我们相信"),
            new Item("about.values.sub", ABOUT, "我们相信", "区块说明", false, false,
                    "【请填写：说明一下排序规则，或留空。】"),
            new Item(KEY_VALUES_TAGS, ABOUT, "我们相信", "价值观标签（**一行一个**）", true, false,
                    "【价值观 1】"),

            // ==================== 社团介绍页 · 怎么加入（4 项）====================
            new Item("about.join.title", ABOUT, "怎么加入", "区块标题", false, false, "怎么加入"),
            new Item("about.join.cardTitle", ABOUT, "怎么加入", "卡片标题", false, false, "在线注册"),
            new Item("about.join.cardBody", ABOUT, "怎么加入", "卡片正文（支持 Markdown）", true, true,
                    "【请填写：注册流程说明。】"),
            new Item("about.join.note", ABOUT, "怎么加入", "区块备注", true, true,
                    "【请填写：你们的招新时间与安排。】"),

            // ==================== 社团介绍页 · 常见问题（11 项：5 问 5 答）====================
            new Item("about.faq.title", ABOUT, "常见问题", "区块标题", false, false, "常见问题"),
            new Item("about.faq.q1", ABOUT, "常见问题", "问题 1", false, false, "【问题 1】"),
            new Item("about.faq.a1", ABOUT, "常见问题", "回答 1（支持 Markdown）", true, true, "【请填写：回答 1。】"),
            new Item("about.faq.q2", ABOUT, "常见问题", "问题 2", false, false, "【问题 2】"),
            new Item("about.faq.a2", ABOUT, "常见问题", "回答 2（支持 Markdown）", true, true, "【请填写：回答 2。】"),
            new Item("about.faq.q3", ABOUT, "常见问题", "问题 3", false, false, "【问题 3】"),
            new Item("about.faq.a3", ABOUT, "常见问题", "回答 3（支持 Markdown）", true, true, "【请填写：回答 3。】"),
            new Item("about.faq.q4", ABOUT, "常见问题", "问题 4", false, false, "【问题 4】"),
            new Item("about.faq.a4", ABOUT, "常见问题", "回答 4（支持 Markdown）", true, true, "【请填写：回答 4。】"),
            new Item("about.faq.q5", ABOUT, "常见问题", "问题 5", false, false, "【问题 5】"),
            new Item("about.faq.a5", ABOUT, "常见问题", "回答 5（支持 Markdown）", true, true, "【请填写：回答 5。】"),

            // ==================== 社团介绍页 · 社训与号召（2 项）====================
            new Item("about.motto", ABOUT, "社训与号召", "社训（页面上一行大字）", false, false, "请输入您的社训"),
            new Item("about.cta.text", ABOUT, "社训与号召", "号召语", true, false, "【请填写：一句号召的话。】")
    );

    private SiteTextKeys() {
    }

    /** 键是否登记过（保存时用它做白名单） */
    public static boolean contains(String key) {
        return byKey(key) != null;
    }

    /** 按 key 找这一项；没有就返回 null */
    public static Item byKey(String key) {
        if (key == null) {
            return null;
        }
        for (Item it : ALL) {
            if (it.getKey().equals(key)) {
                return it;
            }
        }
        return null;
    }

    /** 配置为空时该显示什么（没登记过的键 → 给一句通用占位符） */
    public static String placeholderOf(String key) {
        Item it = byKey(key);
        return it == null ? "【请填写】" : it.getPlaceholder();
    }

    /** 某个大面板下，所有的折叠子项名（按登记顺序，去重） */
    public static List<String> blocksOf(String group) {
        List<String> out = new ArrayList<>();
        for (Item it : ALL) {
            if (it.getGroup().equals(group) && !out.contains(it.getBlock())) {
                out.add(it.getBlock());
            }
        }
        return out;
    }

    /** 某个折叠子项下的所有项（按登记顺序） */
    public static List<Item> itemsOf(String group, String block) {
        List<Item> out = new ArrayList<>();
        for (Item it : ALL) {
            if (it.getGroup().equals(group) && it.getBlock().equals(block)) {
                out.add(it);
            }
        }
        return out;
    }

    /** 把「一行一个」的文本切成列表（价值观标签墙用） */
    public static List<String> splitLines(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String line : value.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 给后台表单用的分组结构：面板 → 子项 → 项（纯数据，模板直接循环） */
    public static Map<String, Map<String, List<Item>>> grouped() {
        Map<String, Map<String, List<Item>>> out = new LinkedHashMap<>();
        for (String group : List.of(GROUP_SITE, GROUP_HOME, GROUP_ABOUT)) {
            Map<String, List<Item>> blocks = new LinkedHashMap<>();
            for (String block : blocksOf(group)) {
                blocks.put(block, itemsOf(group, block));
            }
            out.put(group, blocks);
        }
        return out;
    }
}
