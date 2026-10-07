/*
 * 极简 Markdown 渲染器（T-024）—— 零依赖、无 CDN。
 *
 * 【为什么自己写，不用现成的库】
 *   ① 引外部库就要引 CDN；本项目已经在 ECharts 上栽过一次
 *      （CDN 加载失败 → 整个功能空白，而 AI 沙箱没外网、没法验证）；
 *   ② 我们要的功能很少：标题 / 加粗 / 列表 / 链接 / 图片 / 代码 / 引用。
 *      自己写 40 行就够，而且【每一行都看得懂、能改】。
 *
 * ⚠️⚠️ 安全（最重要的一条）
 *   必须【先把 HTML 转义，再解析 Markdown】！
 *   顺序反了的话，正文里写 <script>alert(1)</script> 就会被真的执行（XSS）。
 *   转义之后它变成 &lt;script&gt;...，**只会被当文字显示**，不会执行。
 *   另外还要挡掉 javascript: 开头的链接。
 *
 * 【最坏情况】就算这个脚本没加载，页面显示的也是 Markdown 原文 ——
 *   内容照样看得见，**不会白屏**。这点比 ECharts 那次安全得多。
 */

/* 转义 HTML —— 防 XSS 的第一道、也是最重要的一道关 */
function mdEscape(s) {
    return String(s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

/* 只允许安全的链接：http / https / 站内绝对或相对路径；挡住 javascript: / data: */
function mdSafeUrl(u) {
    var s = String(u).trim();
    var low = s.toLowerCase();
    if (low.indexOf("javascript:") === 0 || low.indexOf("data:") === 0 || low.indexOf("vbscript:") === 0) {
        return "#";
    }
    return s;
}

function mdToHtml(src) {
    if (!src) { return ""; }
    /* ① 先转义！这一步在解析之前，绝不能挪到最后 */
    var s = mdEscape(src);

    /* ② 代码块 ```...``` */
    s = s.replace(/```([\s\S]*?)```/g, function (_, code) {
        return "<pre class=\"md-code\">" + code.replace(/^\n/, "") + "</pre>";
    });
    /* ③ 行内代码 `x` */
    s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
    /* ④ 图片 ![说明](路径) */
    s = s.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, function (_, alt, url) {
        return "<img class=\"md-img\" alt=\"" + alt + "\" src=\"" + mdSafeUrl(url) + "\">";
    });
    /* ⑤ 链接 [文字](路径) */
    s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, function (_, text, url) {
        return "<a href=\"" + mdSafeUrl(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + text + "</a>";
    });
    /* ⑥ 加粗、斜体 */
    s = s.replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>");
    s = s.replace(/\*([^*]+)\*/g, "<i>$1</i>");
    /* ⑦ 标题（从多到少，避免 # 先匹配掉 ##） */
    s = s.replace(/^###### (.*)$/gm, "<h6>$1</h6>");
    s = s.replace(/^##### (.*)$/gm, "<h5>$1</h5>");
    s = s.replace(/^#### (.*)$/gm, "<h4>$1</h4>");
    s = s.replace(/^### (.*)$/gm, "<h3>$1</h3>");
    s = s.replace(/^## (.*)$/gm, "<h2>$1</h2>");
    s = s.replace(/^# (.*)$/gm, "<h1>$1</h1>");
    /* ⑧ 引用（> 已经被转义成 &gt;） */
    s = s.replace(/^&gt; (.*)$/gm, "<blockquote>$1</blockquote>");
    /* ⑨ 无序列表 */
    s = s.replace(/^[-*] (.*)$/gm, "<li>$1</li>");
    s = s.replace(/(?:^|\n)((?:<li>[\s\S]*?<\/li>\n?)+)/g, "\n<ul>$1</ul>");
    /* ⑩ 剩下的按空行切段，段内换行变 <br> */
    s = s.split(/\n{2,}/).map(function (block) {
        var t = block.trim();
        if (!t) { return ""; }
        if (/^<(h\d|ul|ol|pre|blockquote|li)/.test(t)) { return t; }
        return "<p>" + t.replace(/\n/g, "<br>") + "</p>";
    }).join("\n");
    return s;
}

/* ============ 编辑器：工具栏 + 实时预览（只给新闻表单用） ============ */
function mdInitEditor(textareaId, previewId) {
    var ta = document.getElementById(textareaId);
    var pv = document.getElementById(previewId);
    if (!ta || !pv) { return; }

    function refresh() { pv.innerHTML = mdToHtml(ta.value); }
    ta.addEventListener("input", refresh);
    refresh();

    /* 往光标处插一段文本（没有光标就插到末尾） */
    function insert(before, after, placeholder) {
        var start = ta.selectionStart, end = ta.selectionEnd;
        var sel = ta.value.slice(start, end) || placeholder || "";
        var text = before + sel + after;
        ta.value = ta.value.slice(0, start) + text + ta.value.slice(end);
        ta.selectionStart = ta.selectionEnd = start + before.length + sel.length;
        ta.focus();
        refresh();
    }

    /* 给按钮挂事件：按钮上写 data-md="**|**|粗体占位" 即可 */
    var bar = document.querySelector("[data-md-toolbar]");
    if (!bar) { return; }
    bar.addEventListener("click", function (ev) {
        var btn = ev.target.closest("[data-md]");
        if (!btn) { return; }
        ev.preventDefault();
        var parts = btn.getAttribute("data-md").split("|");
        insert(parts[0], parts[1], parts[2]);
    });
}
