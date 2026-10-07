package com.fountainhub.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常兜底（R-001）。
 *
 * 【它解决什么问题】
 *   ① 让用户看到我们自己风格的中文错误页，而不是 Spring 的英文白板页（Whitelabel Error Page）
 *   ② 把异常**完整记进日志**（带 URL 和用户名），方便排查
 *
 * 【本类只新增，不改动任何已有代码】—— 不碰 SecurityConfig、不碰任何 Controller。
 *
 * ===========================================================================
 * ⚠️⚠️ 写这个类踩了 **三次** 同一个坑，务必看懂再改 ⚠️⚠️
 *
 * 坑的名字叫：**兜底处理器把"别人该管的状态码"吞掉了**。
 *
 *   ① `AccessDeniedException` 被吞  → 权限拒绝 **403 变成 500**
 *   ② `NoResourceFoundException` 被吞 → 页面不存在 **404 变成 500**（实测确认）
 *   ③ `MethodArgumentTypeMismatchException` 被吞 → 参数不合法 **400 变成 500**（实测确认）
 *
 * 【根本原因】
 *   `@ExceptionHandler` 方法一旦 return 了视图名，HTTP 状态码就由**我们**决定了。
 *   如果写 `@ResponseStatus(INTERNAL_SERVER_ERROR)`，那**所有**异常都会变成 500 —— 即使
 *   Spring 本来能把它正确识别成 400 / 404 / 405。
 *
 * 【正确的做法（本类采用）】
 *   ✅ 只负责【记日志】，然后**把异常原样抛回去**，交给 Spring 自己的异常解析器 ——
 *      它比我更清楚每种异常该对应哪个状态码。
 *   ✅ 明确的 404 单独处理（因为它抛回去之后没人管状态码，到容器那层会变 500）。
 * ===========================================================================
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 【1】权限拒绝：**原样抛回去**，交给 Spring Security 渲染 403 页。
     *
     * `@PreAuthorize` 拒绝访问时抛的就是它。
     * 如果被兜底方法接住并返回 500 页面，用户会以为"网站崩了"，而不是"我没权限"。
     * （T-012 还踩过一次同类坑：403 变成了 **405** —— 因为 accessDeniedPage 是转发且保留原方法）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    /**
     * 【2】「找不到」：明确保持 **404**，渲染 404 页。
     *
     * Spring Boot 3.2 之后，访问不存在的网址抛的是 `NoResourceFoundException`。
     *
     * 为什么不学【1】那样"抛回去"？—— 因为权限拒绝后面有 Security 过滤器接着管，
     * 抛回去有人接；而"找不到"抛回去之后**没人再管状态码**，到容器那层就变 500 了。
     * 所以这一类必须我们亲自把 404 说清楚。
     *
     * 404 属于正常噪音（用户手抖、爬虫），**不记日志**，免得刷屏。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Exception e) {
        return "error/404";
    }

    /**
     * 【3】真正的兜底：**只记日志，不改状态码**。
     *
     * 这里**故意**不 return 任何视图 —— 而是把异常原样抛回去。
     * 这样 Spring 的异常解析器链会继续往下走：
     *   - 参数类型不对      → 400
     *   - 请求方法不支持    → 405
     *   - 真正的代码 bug    → 500（并自动使用 templates/error/500.html）
     *
     * 副作用：日志里会多一条 "Failure in @ExceptionHandler" 的 WARN ——
     * 这是正常的，它表示"我处理完就放走了"，**不是错误**。
     */
    /**
     * 上传文件太大（T-025）。
     * 【为什么要单独处理】不处理的话会落到最下面的 catch-all，被记成 ERROR + 返回 500 页面 ——
     *   但"文件太大"**不是程序出错**，是用户操作问题，应该给一句人话。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> handleTooLarge() {
        // 【为什么返回 JSON 而不是错误页】
        //   上传是前端用 fetch 调的，它期待的是 {"ok":false,"message":"..."}。
        //   返回 HTML 错误页的话，前端 .json() 会解析失败 → 用户只看到"没反应" ❌。
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", false);
        r.put("message", "图片太大了（单张不能超过 5MB），换一张小一点的吧。");
        return r;
    }

    /**
     * 【T-026 加的】ResponseStatusException 要**原样放行、且不记 ERROR**。
     *
     * 为什么：公开新闻页遇到"草稿"或"仅登录可见"时，我们主动抛 404
     * （用 404 而不是 403，是为了**连"存在"都不暴露**）。
     * 但那个 404 是**预期行为**，不是程序出错 ——
     * 如果被最下面的 catch-all 接住，日志里就会刷一堆 ERROR，真出问题时就淹没了。
     *
     * 这里什么都不做、直接 rethrow：交给 Spring 按它自己的状态码处理 ✅
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public void handleResponseStatus(org.springframework.web.server.ResponseStatusException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public void logAndRethrow(Exception e, HttpServletRequest request) throws Exception {
        log.error("未预期的异常  URL={}  用户={}", request.getRequestURI(), request.getRemoteUser(), e);
        throw e;
    }
}
