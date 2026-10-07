package com.fountainhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 基础配置 —— 决定「哪些页面不用登录就能看」。
 *
 * 【最重要的一句话】
 *   装上 Spring Security 之后，**所有请求默认都需要登录**。
 *   所以下面那份 permitAll 清单就是"白名单"：没写进去的，游客一律被拦到登录页。
 *   【漏写一个路径 = 那个页面游客就看不了了】。
 *
 * 【本任务只做"基础配置"】
 *   角色规则（比如「只有社长能进 /admin」）是 T-007 的事；
 *   登录页是 T-005、真正的用户来源（查数据库）是 T-006。
 *   本类此刻只是把"门"装上，还没装"门禁读卡器"。
 *
 * 【为什么放行清单里有 /error】
 *   这是 Spring Boot 处理错误时内部转发的路径。
 *   如果不放行，一旦发生 404 / 500，Spring 会把你重定向到登录页，
 *   于是"页面报错"看起来像"没登录"，排查问题会变得非常痛苦。
 */
@Configuration
@EnableWebSecurity
// T-012：开启【方法级】权限控制，这样就能在 Controller 的方法上写：
//     `@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT')")`
// 用来表达"副社长不能管理账号"这类【细粒度】规则 —— URL 级别的规则（/admin/**）做不到这么细。
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 密码编码器。
     * 【为什么做成 Bean，而不是每次 new】
     *   全局只有一个实例、一处配置。将来要换算法或调强度（比如从 10 调到 12），
     *   只改这一行；而且注入到 DataInitializer / T-006 的登录逻辑里，
     *   能保证"加密"和"校验"用的绝对是同一套算法。
     *
     * 【为什么必须是 BCrypt】
     *   见 docs/spec.md 第 3.3 节硬性红线：密码必须 BCrypt 哈希存储、不得出现明文。
     *   BCrypt 的特点是"故意算得慢"（默认 10 轮 = 2^10 次迭代），
     *   慢到暴力破解不划算；同时自带随机盐，同一个密码每次加密结果都不同。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤链 —— 请求进来时先经过它，由它决定"放行还是拦下"。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // ---- 1. 游客必须能看的公开页面（见 docs/spec.md 第 4.1 节）----
                //   T-033：/awards 获奖展示墙 —— FR-8.7 明确要求「不登录即可浏览」
                .requestMatchers("/", "/ping", "/about", "/news/**", "/awards").permitAll()

                // T-011：学员自助注册页，必须游客也能访问（否则没法注册）
                .requestMatchers("/register", "/forgot").permitAll()

                // ---- 2. 静态资源。不放行的话，页面的 CSS/JS 都要登录，等于整站废掉 ----
                //   ⚠️【2026-10-04 补的一个真 bug】这里原来【漏了 /uploads/**】——
                //      于是所有上传的图片（新闻封面、社团介绍页配图、首页背景图…）
                //      对游客都返回 302 跳登录页，**公开页面上全成了裂图**。
                //      为什么以前没发现：验证时只看了"页面 HTML 里有没有图片 URL"，
                //      **没有真的去取那串 URL 的字节** —— 页面看着"有图"，实际取不到。
                //      安全说明：uploads/ 里只有上传白名单内的图片（jpg/jpeg/png/gif/webp），
                //      文件名是 UUID（不可猜）；获奖证书照片的 URL 不会出现在任何公开页面。
                .requestMatchers("/css/**", "/js/**", "/images/**", "/uploads/**", "/webjars/**", "/favicon.ico").permitAll()

                // ---- 3. 登录/登出/错误页本身当然不能拦，否则会无限重定向 ----
                .requestMatchers("/login", "/logout", "/error", "/403").permitAll()

                // ---- 4. T-007：后台只有「社长层 + 指导老师」能进 ----
                //   hasAnyRole("TEACHER") 会自动去找权限名 "ROLE_TEACHER"，
                //   这个名字是 T-006 的 DbUserDetailsService 里拼出来的 —— 两边必须一致。
                //   ⚠️ 注意这只是【粗粒度】的"能不能进后台"。
                //      「副社长不能删数据/管账号」这种【按接口】的细粒度规则，
                //      要等 T-011 / T-012 有具体页面时再加。
                .requestMatchers("/admin/**").hasAnyRole("TEACHER", "PRESIDENT", "VICE_PRESIDENT")

                // ---- 5. 个人中心：任何已登录的人都能进（改密码、看自己的资料）----
                .requestMatchers("/me/**").authenticated()

                // ---- 6. 其余一律需要登录 ----
                .anyRequest().authenticated()
            )
            // T-007：越权（已登录、但角色不够）→ 显示我们自己的 403 页面
            //   注意区分两种情况：
            //     未登录          → 被送去登录页（T-004 就生效了）
            //     已登录但权限不足 → 走这里，显示 403
            .exceptionHandling(e -> e.accessDeniedPage("/403"))
            // T-005：换成我们自己的登录页（GET /login 由 LoginController 渲染成 login.html）
            //   登录成功 → 默认跳回「你原本想访问的那个页面」
            //   登录失败 → 跳 /login?error（页面据此显示"账号或密码错误"）
            //   .permitAll() 很关键：登录页自己当然不能要求先登录，否则会无限重定向
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll())
            .logout(Customizer.withDefaults());

        return http.build();
    }
}
