package com.fountainhub.security;

import com.fountainhub.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 「首次登录必须先改密码」的拦截器。
 *
 * ===========================================================================
 * 【它干什么】
 *   已经登录、且 `users.must_change_password = 1` 的人，
 *   除了「改密码页」和「退出登录」之外，**访问任何页面都会被弹回 /me/password**。
 *
 * 【为什么必须做在服务端（拦截器），而不是页面里写 JS 跳转】
 *   JS 可以关掉、可以直接敲 URL 绕过。
 *   「不改完不许去别的地方」是一条**规则**，规则必须在服务端强制执行 ——
 *   这和"藏按钮不等于没权限"（见 PasswordResetReviewPolicy 的注释）是同一个道理 ✅
 *
 * 【白名单为什么要包含静态资源】
 *   ⚠️ 拦截器对**静态资源也会执行**（Spring 里 `ResourceHttpRequestHandler` 同样是 handler）。
 *   如果 /css/** 、/js/** 不放进白名单，改密页会**没有样式**（裸 HTML）—— 很难看，
 *   而且会让人误以为系统坏了。所以静态路径必须放行 ✅
 *   （WebMvcConfig 里还额外排除了静态路径（少查几次库），省掉每张图都查一次库。）
 *
 * 【为什么白名单里有 /error 和 /403】
 *   如果不放行，一旦页面出错，Spring 内部转发到 /error 时又会被拦回改密页 →
 *   用户看到的永远是改密页，**真正的错误被掩盖**，排查会非常痛苦。
 *   （SecurityConfig 里当年给 /error 放行也是同样的理由。）
 *
 * 【成本】
 *   每请求查一次 users（当前登录用户）。只在已登录时查，游客直接放行。
 *   社团系统的请求量下完全可接受；换来的是"绕不过去"的保证 ✅
 *
 * 【已知行为】
 *   改密码成功后 PasswordController 会把该列置 0 → 下一次请求就不再被拦 ✅
 *   （紧接着会强制重新登录，见 PasswordController.changePassword 的注释。）
 * ===========================================================================
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    /**
     * 强制改密期间【唯一允许访问】的路径前缀。
     * ⚠️ 少一个都会出问题：
     *    · 少了 /me/password  → **无限重定向**（弹到改密页又被弹回改密页）
     *    · 少了 /logout        → 用户被困住，连退都退不出去
     *    · 少了静态资源        → 改密页没有样式
     *    · 少了 /error         → 出错了会被掩盖成"又在改密页"
     */
    private static final List<String> ALLOWED = List.of(
            "/me/password",   // 改密页本身（GET 显示表单 + POST 提交）
            "/logout",        // 退出登录（POST）
            "/login",         // 会话过期后跳登录页时不会被二次拦截
            "/error",         // 错误页（不放行会掩盖真实错误）
            "/403",           // 权限不足页
            "/ping",          // 健康检查
            "/css/", "/js/", "/images/", "/uploads/", "/webjars/", "/favicon.ico"
    );

    private final CurrentUserService currentUserService;

    public MustChangePasswordInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 游客：本来就会被 Spring Security 拦去登录页，这里不用管
        if (currentUserService.isAnonymous(auth)) {
            return true;
        }

        User me = currentUserService.find(auth).orElse(null);

        // 查不到人（理论不该发生）或不需要改密 → 放行
        if (me == null || !Boolean.TRUE.equals(me.getMustChangePassword())) {
            return true;
        }

        if (isAllowed(request.getRequestURI())) {
            return true;
        }

        // 弹回改密页。带上 ?mustChange=1 只是方便排查（页面提示其实读的是模型属性）
        response.sendRedirect(request.getContextPath() + "/me/password?mustChange=1");
        return false;
    }

    /** 路径是否在白名单里（前缀匹配：/css/ 能匹配 /css/fh.css） */
    private boolean isAllowed(String path) {
        if (path == null) {
            return false;
        }
        for (String allowed : ALLOWED) {
            if (path.equals(allowed) || path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }
}
