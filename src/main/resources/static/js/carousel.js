/* ============================================================================
 * carousel.js · 首页焦点图轮播（T-041）
 * ============================================================================
 * 【为什么自己写，不用第三方库】
 *   ① 项目原则是「不引新依赖」；
 *   ② 需求很简单（切换 / 圆点 / 自动播 / hover 暂停），几十行就够；
 *   ③ 第三方轮播库会让首屏多一个网络请求，沙箱环境还可能加载不到。
 *
 * 【怎么用】模板里给容器加 data-carousel，里面放若干 data-slide 和 data-dot：
 *   <div data-carousel>
 *     <div data-slide class="...">...</div>   ← 每一张
 *     <button data-prev>‹</button>
 *     <button data-next>›</button>
 *     <span data-dot></span>                  ← 圆点（下标与 slide 一一对应）
 *   </div>
 *
 * 【无 JS 时的降级】CSS 里默认让第一张 is-on（可见），所以脚本没跑也能看到第一张，
 *   不会白屏 —— javascript:; 而不是 href="#" 是为了避免点箭头时页面跳回顶部。
 * ========================================================================== */
(function () {
    'use strict';

    function init(root) {
        var slides = root.querySelectorAll('[data-slide]');
        var dots = root.querySelectorAll('[data-dot]');
        if (slides.length === 0) {
            return;
        }

        var index = 0;
        var timer = null;
        var INTERVAL = 5000;   // 5 秒自动切一张（设计稿要求）

        function show(n) {
            index = (n + slides.length) % slides.length;   // 循环
            for (var k = 0; k < slides.length; k++) {
                if (k === index) {
                    slides[k].classList.add('is-on');
                } else {
                    slides[k].classList.remove('is-on');
                }
                if (dots[k]) {
                    if (k === index) {
                        dots[k].classList.add('is-on');
                    } else {
                        dots[k].classList.remove('is-on');
                    }
                }
            }
        }

        function play() {
            stop();
            if (slides.length > 1) {
                timer = setInterval(function () { show(index + 1); }, INTERVAL);
            }
        }

        function stop() {
            if (timer) {
                clearInterval(timer);
                timer = null;
            }
        }

        var prev = root.querySelector('[data-prev]');
        var next = root.querySelector('[data-next]');
        if (prev) {
            prev.addEventListener('click', function () { show(index - 1); play(); });
        }
        if (next) {
            next.addEventListener('click', function () { show(index + 1); play(); });
        }
        for (var d = 0; d < dots.length; d++) {
            (function (n) {
                dots[n].addEventListener('click', function () { show(n); play(); });
            })(d);
        }

        // 鼠标移上去暂停，移开继续（设计稿要求）
        root.addEventListener('mouseenter', stop);
        root.addEventListener('mouseleave', play);

        show(0);
        play();
    }

    var all = document.querySelectorAll('[data-carousel]');
    for (var i = 0; i < all.length; i++) {
        init(all[i]);
    }
})();
