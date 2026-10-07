/* ============================================================================
 * crop.js · 封面图裁剪（3 : 1 → 1200 × 400）
 * ============================================================================
 * 【为什么需要它】
 *   首页轮播尺寸是 1200 × 400（3:1）。用户随手传的图可能是 4:3 / 16:9 / 竖图，
 *   直接 object-fit:cover 硬裁会出现「人脸被切一半」「标题被裁掉」。
 *   所以上传【封面图】前先让用户自己框一下 —— 所见即所得。
 *
 * 【为什么自己写 Canvas，不引 Cropper.js】
 *   ① 项目原则：不新增依赖；
 *   ② 需求很简单（拖动 + 缩放 + 固定 3:1），不需要旋转/翻转/自由比例；
 *   ③ 第三方库在沙箱里还可能加载不到。
 *
 * 【用法】 fhCrop(file, function (blob) { ... });  blob 为 null 表示失败/取消
 * ========================================================================== */
(function () {
    'use strict';

    var OUT_W = 1200, OUT_H = 400;   // 输出尺寸（与轮播一致）
    var img = null, cb = null;
    var scale = 1, minScale = 1;
    var offX = 0, offY = 0;
    var stage, canvas, zoom;
    var dragging = false, lastX = 0, lastY = 0;

    function $(id) { return document.getElementById(id); }

    function build() {
        /* 【2026-10-04 修的真 bug】
           原来这里直接 return，但 stage / canvas / zoom 这三个变量指向的是
           【上一次那个已经被删除的元素】。于是第二次打开裁剪框时：
             stage.clientWidth = 0  →  换算系数 k 变成 Infinity
             → offX 变成 NaN → 【拖动完全失效】（用户反馈的只能放大不能拖就是这个）
           修法：早退之前，先把活着的元素重新查一遍。 */
        if ($('fhCropMask')) {
            stage = document.querySelector('.fh-cropstage');
            canvas = $('fhCropCanvas');
            zoom = $('fhCropZoom');
            return;
        }
        var div = document.createElement('div');
        div.id = 'fhCropMask';
        div.className = 'fh-cropmask';
        div.innerHTML =
            '<div class="fh-cropbox">' +
            '  <p class="fh-crop-title">裁剪封面图<span>比例 3 : 1（输出 1200 × 400）</span></p>' +
            '  <div class="fh-cropstage"><canvas id="fhCropCanvas" width="' + OUT_W + '" height="' + OUT_H + '"></canvas></div>' +
            '  <div class="fh-cropctl">' +
            '    <span>缩放</span><input type="range" id="fhCropZoom" min="100" max="300" value="100">' +
            '    <span class="fh-hint">按住图片拖动可调整位置（竖图通常只能上下拖，这是正常的）</span>' +
            '  </div>' +
            '  <div class="fh-cropbtns">' +
            '    <button type="button" class="fh-btn" id="fhCropCancel">取消</button>' +
            '    <button type="button" class="fh-btn fh-btn-main" id="fhCropOk">确定裁剪并上传</button>' +
            '  </div>' +
            '</div>';
        document.body.appendChild(div);

        stage = div.querySelector('.fh-cropstage');
        canvas = $('fhCropCanvas');
        zoom = $('fhCropZoom');

        stage.addEventListener('mousedown', function (e) { dragging = true; lastX = e.clientX; lastY = e.clientY; e.preventDefault(); });
        document.addEventListener('mousemove', function (e) {
            if (!dragging) { return; }
            /* 兜底：万一 stage 取不到宽度（0），按 1 处理，绝不让它变成 Infinity / NaN */
            var sw = (stage && stage.clientWidth) ? stage.clientWidth : OUT_W;
            var k = OUT_W / sw;
            offX += (e.clientX - lastX) * k;
            offY += (e.clientY - lastY) * k;
            lastX = e.clientX; lastY = e.clientY;
            clamp(); draw();
        });
        document.addEventListener('mouseup', function () { dragging = false; });
        zoom.addEventListener('input', function () { scale = minScale * (zoom.value / 100); clamp(); draw(); });
        $('fhCropCancel').addEventListener('click', cancel)   /* ⚠️ 用 cancel，不是 close：取消也要回调一次 cb(null) */;
        $('fhCropOk').addEventListener('click', confirm);
    }

    /* 让图片始终盖满裁剪框（不留白） */
    function clamp() {
        if (!img) { return; }
        var w = img.width * scale, h = img.height * scale;
        if (w <= OUT_W) { offX = (OUT_W - w) / 2; } else { offX = Math.min(0, Math.max(OUT_W - w, offX)); }
        if (h <= OUT_H) { offY = (OUT_H - h) / 2; } else { offY = Math.min(0, Math.max(OUT_H - h, offY)); }
    }

    function draw() {
        var ctx = canvas.getContext('2d');
        ctx.fillStyle = '#F1F5F9';
        ctx.fillRect(0, 0, OUT_W, OUT_H);
        if (img) { ctx.drawImage(img, offX, offY, img.width * scale, img.height * scale); }
    }

    function close() {
        var m = $('fhCropMask');
        if (m) { m.parentNode.removeChild(m); }
        img = null; cb = null;
    }

    /* ⚠️【2026-10-07 修】"取消"也必须回调一次 cb(null)。
       原来 close() 只关框、不回调，于是调用方的提示会永远停在"正在裁剪…"（像卡住了）。 */
    function cancel() {
        var done = cb;      /* 先取出来 —— close() 里会把 cb 置 null */
        close();
        if (done) { done(null); }
    }

    function confirm() {
        var done = cb;
        canvas.toBlob(function (blob) {
            close();
            if (!done) { return; }
            /* 🔄【2026-10-07 修的真 bug —— 这里是"PNG 上传失败"的根因】
               canvas.toBlob() 产出的是一个**裸 Blob**，浏览器给它的 filename 是 "blob"
               —— **没有后缀**。后端是按后缀查白名单的 → 直接被拒，报
                 「只允许上传图片（jpg / jpeg / png / gif / webp）」
               而用户传的明明就是一张 png，所以这个提示会让人完全摸不着头脑 ❌

               ⚠️ 上一版只有"上传首页背景图"那条链路自己在外面包了一层 File，
                  所以背景图能传、新闻封面图不能传 —— 典型的"两条链路行为不一致"。

               ✅ 修法：在【这里】统一包成带后缀的 File，所有调用点都不必再自己包。
               ✅ 老浏览器没有 File 构造器时退回裸 Blob（后端有 Content-Type 兜底，见 FileStorageService）。 */
            done(typeof File === "function"
                    ? new File([blob], "cropped.png", { type: "image/png" })
                    : blob);
        }, 'image/png', 0.92);
    }

    /* 对外入口 */
    window.fhCrop = function (file, callback) {
        build();
        cb = callback;
        var url = URL.createObjectURL(file);
        var im = new Image();
        im.onload = function () {
            img = im;
            minScale = Math.max(OUT_W / im.width, OUT_H / im.height);
            scale = minScale;
            zoom.value = 100;
            offX = (OUT_W - im.width * scale) / 2;
            offY = (OUT_H - im.height * scale) / 2;
            $('fhCropMask').style.display = 'flex';
            clamp(); draw();
            URL.revokeObjectURL(url);
        };
        im.onerror = function () { URL.revokeObjectURL(url); callback(null); };
        im.src = url;
    };
})();
