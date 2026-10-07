package com.fountainhub.config;

import org.springframework.core.env.Environment;

import com.fountainhub.entity.Level;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.StudentProfile;
import com.fountainhub.entity.User;
import com.fountainhub.entity.UserStatus;
import com.fountainhub.repository.StudentProfileRepository;
import com.fountainhub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 启动初始化器 —— 应用每次启动时跑一次，负责建立「系统能跑起来所必需的第一批数据」。
 *
 * 【为什么需要它】
 *   users.password_hash 是 NOT NULL，而 BCrypt 哈希无法手写进 SQL 脚本
 *   （每次加密结果都不同，因为盐是随机的）。
 *   所以初始账号只能由程序在启动时建 —— 这就是本类存在的理由。
 *
 * 【它是怎么被触发的】
 *   CommandLineRunner 是 Spring Boot 的「启动钩子」：
 *   等 Spring 容器和 Web 服务器都起来了之后，自动调用一次 run()。
 *   所以不需要任何人去调它，启动应用就会执行。
 *
 * 【为什么能安全地重复启动】
 *   run() 第一件事就是数一下 users 表里有几个人：
 *   只要不是 0，立刻 return —— 这叫「幂等」（执行一次和执行十次，结果一样）。
 *   所以不管重启多少次，账号都只建一次。
 *
 * ===========================================================================
 * 🔐 【开源脱敏（2026-10-04）：初始密码改由环境变量提供】
 *
 *   以前这里写死了一个明文密码常量。问题有两个：
 *     ① **开源后全世界都能看到那个明文密码**（哪怕只是测试账号，也是坏示范）；
 *     ② 更糟的是：别人把项目 clone 下去一跑，**库里的账号密码居然是公开已知的**。
 *   现在改成：**从环境变量 `FH_INIT_PASSWORD` 读**。
 *     · 没设 → 打一条 WARN 并**跳过建号**（**不设默认值** —— 有默认值等于没脱敏）；
 *     · 设了 → 用它建那 3 个账号。
 *
 *   【怎么用（本机开发）】
 *     PowerShell:  $env:FH_INIT_PASSWORD = '你自己想的密码'; mvn spring-boot:run
 *     IDEA:        Run/Debug Configurations → Environment variables 里加一行
 *     Linux:       export FH_INIT_PASSWORD='...'  然后启动
 *
 *   ⚠️ 注意：本类**只在 users 表为空时**才建号。
 *      所以你现有的库（已有用户）**完全不受影响**，启动时会直接跳过。
 * ===========================================================================
 *
 * 【⚠️ 两个必须知道的点】
 *   1) 判断「有没有初始化过」用的是 count()==0，也就是「表是不是空的」。
 *      假如将来库里先有了学员、却没有指导老师，本类会【跳过】而不补建。
 *
 *   2) BCrypt 同一条密码每次加密出来的字符串都【不一样】（盐随机），
 *      所以不能用「比对哈希」来判断账号是否已存在 —— 只能数行数。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * 初始密码的**环境变量名**（🔐 开源脱敏：代码里不再出现任何明文密码）。
     * 值本身不放代码里 —— 这是本类唯一与"密码"有关的东西，而它只是个变量名。
     */
    private static final String INIT_PASSWORD_ENV = "FH_INIT_PASSWORD";

    private final UserRepository userRepository;

    /**
     * 学员档案仓库。
     * 【为什么需要它】建 `student` 账号时要顺手建一份档案，
     *   否则「我的资料」与「学员详情」上年级/阶段/入社日期全是空的，看不出渲染效果。
     */
    private final StudentProfileRepository profileRepository;

    /**
     * 密码编码器。
     * 【为什么用"注入"而不是 new BCryptPasswordEncoder()】
     *   密码编码器由 SecurityConfig 统一提供一个 Bean。
     *   全局一个实例、一处配置 —— 换算法或调强度只改那一个地方，
     *   也保证"加密"和"校验"用的是同一套算法。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 运行环境 —— 用来判断当前是不是 dev profile。
     *
     * 【为什么要它】初始账号的密码**只在开发期**打印到日志（方便本机测试）；
     *   生产 profile（`--spring.profiles.active=local,prod`）下**不打印**，
     *   免得日志里出现密码。判断逻辑见本类最后的 isDevProfile()。
     */
    private final Environment environment;

    /**
     * 构造器注入。
     * 【为什么不用 @Autowired 字段注入】构造器注入能让字段是 final（不可变），
     *   而且依赖缺失时【启动就报错】，不会等到用到才发现是 null。这是 Spring 官方推荐的方式。
     */
    public DataInitializer(UserRepository userRepository,
                           StudentProfileRepository profileRepository,
                           PasswordEncoder passwordEncoder,
                           Environment environment) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        long userCount = userRepository.count();

        // ---- 幂等保护：只要表里有人，就什么都不做 ----
        if (userCount > 0) {
            log.info("users 表已有 {} 个用户，跳过初始化（这是正常现象，不代表出错）", userCount);
            return;
        }

        // ---- 🔐 从环境变量取初始密码；取不到就【不建号】----
        String initPassword = System.getenv(INIT_PASSWORD_ENV);
        if (initPassword == null || initPassword.isBlank()) {
            log.warn("""
                    users 表为空，但没有设置环境变量 {}，因此【跳过初始账号创建】。
                    这是开源脱敏后的预期行为（代码里不再内置任何明文密码）。
                    要建初始账号，请设置该环境变量后再启动，例如（PowerShell）：
                        $env:{} = '你自己想的密码'; mvn spring-boot:run
                    """, INIT_PASSWORD_ENV, INIT_PASSWORD_ENV);
            return;
        }

        log.warn("检测到 users 表为空，正在创建 3 个测试账号 ...");

        // ---------- ① 指导老师（全部权限，不是"社长"，见 Role.TEACHER） ----------
        User teacher = newUser("teacher", "指导老师", Role.TEACHER, initPassword);
        // 其余字段故意不设：
        //   studentNo / phone  → 工作人员没有学号和手机号，留 NULL
        //   groupId            → 不属于任何小组
        //   lastLoginAt        → 从没登录过，留 NULL
        //   createdAt/updatedAt→ 数据库的 DEFAULT CURRENT_TIMESTAMP 自己填
        //                        （实体上是 insertable=false，程序想写也写不了）
        userRepository.save(teacher);

        // ---------- ② 正社长 ----------
        userRepository.save(newUser("president", "社长", Role.PRESIDENT, initPassword));

        // ---------- ③ 学员（唯一有学号与手机号的人 → 「三种登录方式」要用它测） ----------
        User student = newUser("student", "普通社员", Role.STUDENT, initPassword);
        student.setStudentNo("2025001");
        student.setPhone("13800000003");   // 明显的假号（138-0000-0003），专供演示"手机号也能登录"
        User savedStudent = userRepository.save(student);

        // 顺手给学员建一份档案，否则「我的资料」和「学员详情」上年级/阶段/入社日期全是空
        StudentProfile profile = new StudentProfile();
        profile.setUserId(savedStudent.getId());   // 一对一：档案的主键就是人的 id
        profile.setGrade("2025级");
        profile.setMajorClass("计算机 1 班");
        profile.setLevel(Level.BEGINNER);
        profile.setLanguages("Python,C++");
        profile.setJoinDate(LocalDate.now());      // 数据库那一列是 DATE，只能用 LocalDate
        profileRepository.save(profile);

        log.warn("3 个测试账号已创建（角色分别为 {} / {} / {}）。",
                Role.TEACHER.getLabel(), Role.PRESIDENT.getLabel(), Role.STUDENT.getLabel());

        // ---------- 打印测试账号与密码 ----------
        // 【为什么要分 profile】
        //   dev（默认 local,dev）→ 打印出来，本机测试一眼就能看到；
        //   prod（--spring.profiles.active=local,prod）→ 只提示"密码来自哪个环境变量"，不打印值。
        // ⚠️ 这几个是【开发测试账号】，正式使用前必须改密码。
        if (isDevProfile()) {
            log.warn("""
                    ======== 测试账号（仅开发期打印）========
                        测试账号 teacher   / 密码 {}
                        测试账号 president / 密码 {}
                        测试账号 student   / 密码 {}   （学号 2025001，手机号 13800000003）
                    ========================================
                    """, initPassword, initPassword, initPassword);
        } else {
            log.warn("初始密码来自环境变量 {}（非 dev 环境不打印密码）。⚠️ 正式使用前请先改掉密码！",
                    INIT_PASSWORD_ENV);
        }
    }

    /**
     * 当前是否处于 dev profile（决定要不要把初始密码打印到日志）。
     * 【为什么不用 @Profile 注解】这里只需要"判断"，不需要"要不要创建 Bean" —— 注解反而更绕。
     */
    private boolean isDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 造一个「干净的」用户：只填账号必需的四样（用户名、密码哈希、姓名、角色/状态）。
     *
     * 【为什么抽成一个小方法】
     *   三个账号只有前四项不同，其余（logisticsDuty=false、status=ACTIVE）完全一样。
     *   抽出来以后，将来再加测试账号只改一行；也避免三处各写一遍、漏掉某一项。
     *
     * 【密码为什么当参数传进来，而不是用类里的常量】
     *   🔐 因为"常量"意味着"写在源码里"，而源码是要开源出去的。
     *   当参数传，密码就只存在于**运行时的环境变量**里，不落在任何一行代码上。
     */
    private User newUser(String username, String realName, Role role, String rawPassword) {
        User u = new User();
        u.setUsername(username);
        // 关键一步：明文密码立刻交给 BCrypt 变成 60 位哈希，数据库里永远不出现明文
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setRealName(realName);
        u.setRole(role);
        u.setLogisticsDuty(false);       // 不是「兼后勤」的角色
        u.setStatus(UserStatus.ACTIVE);  // 在读：可以登录
        return u;
    }
}
