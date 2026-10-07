package com.fountainhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fountainhub.security.MustChangePasswordInterceptor;

import java.nio.file.Paths;

/**
 * 把上传目录映射成可访问的 URL（T-025）。
 *
 * ===========================================================================
 * 【为什么单独写这个类，而不是在 application.yml 里配 static-locations】
 *   第一版我在 yml 里写的是 `file:./uploads/` —— 结果是
 *   **图片上传成功了，但浏览器访问 /uploads/xxx.png 是 404** ❌
 *   原因：`./` 是"相对于进程的工作目录"，在不同启动方式下（IDEA / mvn / jar）
 *   到底指哪里**说不准**。
 *
 *   这里改成：**先把上传目录解析成绝对路径**，再挂上去 —— 路径是什么就是什么，
 *   没有"看情况"的余地 ✅
 * ===========================================================================
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${fountainhub.upload.dir:./uploads/}")
    private String uploadDir;

    /** 首次登录强制改密的拦截器（2026-10-07）。逻辑在它自己类里，这里只负责挂上去 */
    private final MustChangePasswordInterceptor mustChangePasswordInterceptor;

    public WebMvcConfig(MustChangePasswordInterceptor mustChangePasswordInterceptor) {
        this.mustChangePasswordInterceptor = mustChangePasswordInterceptor;
    }

    /**
     * 把「首次登录必须先改密」的拦截器挂到全站。
     *
     * ⚠️ 这里排除静态资源只是为了【少查几次库】；
     *   拦截器自己内部还有一份白名单（改密页 / 登出 / 错误页…），
     *   两份都要有 —— 少任何一份都会出问题（详见拦截器的类注释）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mustChangePasswordInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/webjars/**", "/favicon.ico");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 解析成绝对路径（和 FileStorageService 落盘时用的是同一套逻辑，保证两边一致）
        String absolute = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        // Windows 下 toUri 会给成 file:/D:/... 的形式，这里手工拼成 Spring 认识的 file: 前缀
        String location = "file:" + absolute.replace("\\", "/") + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
