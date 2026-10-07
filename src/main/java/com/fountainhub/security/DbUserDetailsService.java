package com.fountainhub.security;

import com.fountainhub.entity.User;
import com.fountainhub.entity.UserStatus;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据库用户服务 —— 登录时「按输入的账号去数据库里找人」。
 *
 * 【它在登录流程里的位置】
 *   1. 用户在登录页输入「账号 + 密码」，提交 POST /login
 *   2. Spring Security 的过滤器拦下这个请求，调用本类的 loadUserByUsername(账号)
 *   3. 本类去数据库把人找出来，连同【密码哈希】和【角色】一起交给 Spring
 *   4. Spring 拿用户输入的明文密码，用 PasswordEncoder（BCrypt）跟哈希比对
 *      → 对得上且账号状态允许，就登录成功
 *
 *   ⚠️ 注意第 4 步：**比对密码这件事我们一行代码都不写**，全部交给框架。
 *      安全相关的代码，交给久经考验的框架比自己写靠谱得多。
 *
 * 【三种登录方式是怎么实现的】
 *   用户在「账号」框里可能填 用户名 / 学号 / 手机号 中的任意一种
 *   （用户名 / 学号 / 手机号 三种都算"账号"），所以这里【依次尝试】三种查询，命中一个就够：
 *       findByUsername → findByStudentNo → findByPhone
 *   这三个方法就是 T-002 写的 —— 到这里才第一次被真正调用。
 *
 * 【为什么要用数据库里的 username，而不是用户输入的原文】
 *   如果用户用学号登录，而我们把"学号"当作 username 塞进 UserDetails，
 *   那后续所有代码都得判断"这次到底是哪种方式进来的"。
 *   统一成数据库里的 username 之后，系统里就只有一个身份标识，省掉无数麻烦。
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    /**
     * 允许登录的账号状态。
     *
     * 【为什么是这三个】
     *   ACTIVE（在读）  —— 当然可以登录
     *   GRADUATED（毕业）—— 项目负责人 2026-10-02 决定：毕业生仍可登录回来看自己的记录
     *   LEFT（退社）    —— 【2026-10-07 项目负责人决定】退社的人**可以登录**了，
     *                      但只能看公开页面和自己的个人主页，**绝对进不了后台**。
     *                      怎么做到的见下面 NO_BUSINESS_ROLE 的注释（关键：不发角色牌）。
     *   DISABLED（禁用）—— 不允许（管理员手动停用，比如违规）
     *
     * 【为什么不用 == ACTIVE 一句写完】
     *   写成"白名单集合"之后，将来要加一种可登录状态，只改这一行，逻辑不用动。
     */
    private static final Set<UserStatus> LOGIN_ALLOWED =
            Set.of(UserStatus.ACTIVE, UserStatus.GRADUATED, UserStatus.LEFT);

    /**
     * 允许登录、但【一个业务角色都不发】的状态。
     *
     * ===========================================================================
     * 🔒 【为什么"退社"要这么实现 —— 这是本批改动的核心，别换成别的写法】
     *
     *   需求：LEFT 的人能登录，但访问 /admin/** 必须返回 **403**。
     *
     *   ❌ 错误做法 1：去 SecurityConfig 里加一条"LEFT 不许进 /admin"的规则。
     *      做不到 —— 那边的规则是**按角色**的（hasAnyRole），而"退社"是一种**状态**、
     *      压根不是角色；更何况一个**退社的社长**，users.role 里还写着 PRESIDENT，
     *      他照样有 ROLE_PRESIDENT，那条规则会**直接放行**。
     *
     *   ❌ 错误做法 2：给每个后台接口都加一句"判断状态不是 LEFT"。
     *      后台有几十个接口，**一定会漏**，漏掉的那个就是后门。
     *
     *   ✅ 本类的做法：**退社 = 一张角色牌都不发**（只发一个标记用的 ROLE_LEFT）。
     *      于是 SecurityConfig 里那句
     *          .requestMatchers("/admin/**").hasAnyRole("TEACHER","PRESIDENT","VICE_PRESIDENT")
     *      对 LEFT 用户**天然全部落空** → 自动 403 ✅
     *      —— **一处改动，全站所有后台接口同时生效**，不可能漏。
     *
     *   ✅ /me/** 不受影响：它在 SecurityConfig 里只要求 authenticated()，不看角色。
     *
     *   ⚠️ 已知局限（2026-10-07 项目负责人确认按现状）：这是【登录那一刻】决定的。
     *      若某人正登录着、管理员这时把他改成 LEFT，他当前会话里仍留着旧权限，
     *      **要重新登录才失效** —— 和"改了角色也要重登录"是同一种现象。
     * ===========================================================================
     */
    private static final Set<UserStatus> NO_BUSINESS_ROLE =
            Set.of(UserStatus.LEFT);

    /** 退社的人只拿到这一个权限：不是业务角色，纯粹用来标记身份（便于以后扩展与排查） */
    private static final String ROLE_LEFT = "ROLE_LEFT";

    private final UserRepository userRepository;
    private final StudyGroupRepository studyGroupRepository;

    /**
     * 构造器注入。
     * 注意这里注入的是 T-002 写的 UserRepository —— 本类是它的第一个真正使用者。
     * studyGroupRepository 是 T-016 加的：用来判断"这个人是不是某个组的组长"。
     */
    public DbUserDetailsService(UserRepository userRepository,
                                StudyGroupRepository studyGroupRepository) {
        this.userRepository = userRepository;
        this.studyGroupRepository = studyGroupRepository;
    }

    /**
     * Spring Security 在登录时调用这个方法。
     *
     * @param login 用户在「账号」框里输入的原文（可能是用户名、学号或手机号）
     * @return Spring 认识的 UserDetails（含密码哈希与角色）
     * @throws UsernameNotFoundException 查不到人时抛出。
     *         ⚠️ 抛这个异常后，Spring 会统一跳 /login?error，
     *            页面上只显示"账号或密码错误"——【不区分】是"账号不存在"还是"密码错"。
     *            这是刻意的：一旦提示能区分，别人就能靠它试探"某个账号存不存在"（用户枚举漏洞）。
     */
    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        // 去掉首尾空格：用户从别处复制粘贴时经常带上空格，
        // 不 trim 的话会出现"明明是对的却登不进去"这种最难查的问题。
        String key = login == null ? "" : login.trim();

        User user = userRepository.findByUsername(key)
                .or(() -> userRepository.findByStudentNo(key))
                .or(() -> userRepository.findByPhone(key))
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在：" + key));

        boolean canLogin = LOGIN_ALLOWED.contains(user.getStatus());

        // ---------- 组装权限（T-016 起可能不止一个）----------
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (NO_BUSINESS_ROLE.contains(user.getStatus())) {
            // 🔒 退社的人：能登录，但【一个业务角色都不发】—— 原因见 NO_BUSINESS_ROLE 的注释。
            //    这里同时跳过了组长判断：少一次数据库查询，而且退社的人也不该再顶着"组长"身份。
            authorities.add(new SimpleGrantedAuthority(ROLE_LEFT));
        } else {
            // ① 主角色：users.role 里存的那一个
            //    角色 → 权限。Spring 的 hasRole("TEACHER") 会自动去找 "ROLE_TEACHER"
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

            // ② **派生的**组长身份（T-016，方案 B）
            //    定义："我是某个组的组长" = study_groups 里有一行 leader_user_id 是我。
            //    ⚠️ 这个判断**故意不查 users.role** —— 组长身份不从那里存，原因见 entity/Role.java 的 assignable() 注释。
            //    效果：被指定为组长 → 登录时自动多一个 ROLE_LEADER；
            //          被换下来   → ROLE_LEADER 自动消失。**不需要改任何人的主角色**。
            if (studyGroupRepository.existsByLeaderId(user.getId())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_LEADER"));
            }
        }

        return org.springframework.security.core.userdetails.User
                // ⭐ 关键：用数据库里的 username，不是用户输入的原文（见类注释）
                .withUsername(user.getUsername())
                // 给的是【哈希】，Spring 自己拿明文来比对
                .password(user.getPasswordHash())
                .authorities(authorities)
                // disabled = true 时，Spring 会拒绝登录（跳 /login?error）
                .disabled(!canLogin)
                .build();
    }
}
