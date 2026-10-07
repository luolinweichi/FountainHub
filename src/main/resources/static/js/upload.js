/* ============================================================================
 * upload.js · 公共的「上传一张图」函数（T-025 起就有的逻辑，T-042 抽出来共用）
 * ============================================================================
 * 【为什么抽成公共文件】
 *   原来的 fhUpload 是写在 admin/news-form.html 里的——那它是【那个页面的局部函数】。
 *   T-042 的站点设置页也想上传背景图，直接调 fhUpload 就会报 ReferenceError，
 *   用户看到的现象就是「点了没反应 / 一直上传不上去」，而且连报错都没有
 *   （异常发生在事件回调里，不会显示在页面上）。
 *   👉 所以抽到这里，谁要上传就引这个文件。
 *
 * 【用法】fhUpload(file, function (url) { ... }, function (错误信息) { ... });
 *
 * 【为什么要从 meta 取 CSRF】
 *   POST /admin/upload 会被 Spring Security 的 CSRF 过滤器拦下（403）。
 *   页面 <head> 里有 <meta name="_csrf"> 和 <meta name="_csrf_header">，
 *   这里把它们拼成一个请求头带上。那两个 meta 由 layout.html 的公共 head 提供。
 * ========================================================================== */
function fhUpload(file, onOk, onErr) {
    var fd = new FormData();
    fd.append("file", file);

    var mt = document.querySelector('meta[name="_csrf"]');
    var mh = document.querySelector('meta[name="_csrf_header"]');
    if (!mt || !mh) {
        // 这种情况只在「页面没经过 layout 的 head fragment」时出现，报清楚比默默失败好
        onErr("页面缺少 CSRF 标记，请刷新页面重试");
        return;
    }
    var headers = {};
    headers[mh.content] = mt.content;

    fetch("/admin/upload", { method: "POST", body: fd, headers: headers })
        .then(function (r) { return r.json(); })
        .then(function (j) {
            if (j.ok) {
                onOk(j.url);
            } else {
                onErr(j.message || "上传失败");
            }
        })
        .catch(function () {
            // 后端返回的不是 JSON（比如 403 的 HTML 错误页）就会走到这里
            onErr("上传失败，请刷新重试");
        });
}
