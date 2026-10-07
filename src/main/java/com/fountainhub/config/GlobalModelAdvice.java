package com.fountainhub.config;

import com.fountainhub.security.CurrentUserService;
import com.fountainhub.entity.SiteTextKeys;
import com.fountainhub.service.SiteSettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全站公共模型数据。
 *
 * 【它解决什么问题】
 *   导航栏在每个页面上都要显示「系统名 / 社团名 / 你好，XXX」。
 *   如果让每个 Controller 都手动往 Model 里塞这几样，就是重复劳动，还容易漏。
 *   `@ControllerAdvice` + `@ModelAttribute` 的意思是：
 *   **每一个 Controller 方法执行前，都先跑一遍下面这个方法**，把公共数据塞进 Model。
 *
 * 【顺带把写死的东西集中到一处】（REFACTOR_PLAN 的 R-11）
 *   系统名和社团名本来散在 HomeController / LoginController 里，现在只有这一份。
 *   以后要改名字，只改这里。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /** 系统名（全站唯一来源） */
    public static final String SYSTEM_NAME = "FountainHub";

    // 🔴【2026-10-07 改动 —— 别再把社团名写回代码里】
    //   原来这里是两个硬编码常量（社团名 + 口号），社团名写的就是那个通用占位名。
    //   问题：社团名到底显示什么，被写死在 Java 里 —— 管理员在后台改不了，
    //        而且"全站文案可视化配置"这个功能对它完全无效。
    //   ✅ 现在改成从 site_settings 读（键：site.clubName / site.clubSlogan），
    //      在后台 /admin/settings →「全站通用」里改，**导航栏 / 页脚 / 登录弹窗 /
    //      首页大标题 / 介绍页大标题** 会一起变。
    //      没配时显示占位符「【请填写社团名称】」（口号没配就是空，不破坏排版）。
    //   ⚠️ 所以本类现在【不再有任何社团相关的字符串常量】——请保持这样。

    // 🔄 R-007：取当前用户的逻辑统一收进 `CurrentUserService`，这里不再自己写一遍
    private final CurrentUserService currentUserService;

    /**
     * 站点文案（2026-10-07 新增）。
     * 【为什么要注入到这里】首页和社团介绍页的文案现在存在 site_settings 里，
     *   如果让 HomeController / AboutController 各自去查一遍，就是重复劳动、而且容易漏。
     *   挂成**全局模型属性**之后，任何模板都能直接 `${siteText['home.hero.title']}`，
     *   **各个 Controller 一行都不用改** ✅（这也是 GlobalModelAdvice 本来的职责）
     */
    private final SiteSettingService siteSettingService;

    public GlobalModelAdvice(CurrentUserService currentUserService,
                             SiteSettingService siteSettingService) {
        this.currentUserService = currentUserService;
        this.siteSettingService = siteSettingService;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model, Authentication authentication, HttpServletRequest request) {
        model.addAttribute("systemName", SYSTEM_NAME);

        // 🔄 2026-10-07：全站文案（社团名 / 口号 / 首页 / 社团介绍页）**一次查库**全部取出来。
        //   · 键 → 显示值；**配置为空的项，值已经是占位符**（如「【请填写社团名称】」），
        //     所以模板里 `${siteText['xxx']}` 永远非空，不会出现"页面上一片空白" ✅
        //   · 兜底逻辑在 SiteSettingService.allTexts()，这里只是把它挂到全局模型上。
        //   ⚠️【顺序要求】必须先取到这个 Map，下面 clubName / clubSlogan 都从它里面拿 ——
        //      既不重复查库，也避免"用了还没声明的变量"这种编译错误。
        java.util.Map<String, String> texts = siteSettingService.allTexts();
        model.addAttribute("siteText", texts);

        // 🔄 2026-10-07：社团名 / 口号改为**从 site_settings 读**（见文件里那段"别写回代码"的注释）
        model.addAttribute("clubName", texts.get(SiteTextKeys.KEY_CLUB_NAME));
        model.addAttribute("clubSlogan", texts.get(SiteTextKeys.KEY_CLUB_SLOGAN));

        // ⭐ 当前请求路径（T-028 子导航要用它判断"我在哪一页"）
        // 【为什么要从后端传】Thymeleaf 3.1 已经移除了 `#httpServletRequest` 这类内置对象，
        //   所以在模板里拿不到请求路径 —— 由这里统一塞进去最省事。
        model.addAttribute("currentPath", request.getRequestURI());

        // ⚠️ 【T-028 踩的坑，很重要】提前创建 session
        //   原因：Spring Security 的 CSRF token 存在 session 里。
        //     Thymeleaf 渲染 @{...} 表单（th:action）时会去拿 token —— 如果那时还没 session，
        //     它就要**新建**一个；可我们的页面在渲染到那个表单之前，
        //     前面的内容（尤其是 250 行的 <style>）已经超过 Tomcat 的 8KB 响应缓冲、
        //     把响应**提交**出去了 —— 此时再建 session 就会抛
        //       IllegalStateException: Cannot create a session after the response has been committed
        //     结果整个页面 500 ✅
        //   修法：在这里（所有 Controller 之前）就把 session 建好，后面拿 token 就不用新建了。
        request.getSession(true);

        // 游客：不设置 navUserName，导航栏自然会显示"登录/注册"
        // （⚠️ 别用 isAuthenticated() 判断 —— 它对游客也返回 true，原因见 `CurrentUserService`）
        if (currentUserService.isAnonymous(authentication)) {
            return;
        }

        // 已登录：取真实姓名给导航栏显示"你好，XXX"
        // 万一查不到（理论上不该发生），退化成用户名，至少不会空白
        String displayName = currentUserService.find(authentication)
                .map(user -> user.getRealName())
                .orElse(authentication.getName());
        model.addAttribute("navUserName", displayName);
    }
}
