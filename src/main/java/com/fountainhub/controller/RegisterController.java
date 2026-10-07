package com.fountainhub.controller;

import com.fountainhub.dto.RegisterForm;
import com.fountainhub.entity.Level;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.StudentProfile;
import com.fountainhub.entity.User;
import com.fountainhub.entity.UserStatus;
import com.fountainhub.repository.StudentProfileRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.FormUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * 学员自助注册。
 *
 * 【流程】
 *   游客打开 /register 填表 → 提交 → 建账号（状态 = 待审核）+ 建档案
 *   → 管理员在 /admin/students 里审核 → 通过（转为在读）或拒绝（删除）
 *
 * 【本类的两条安全红线，绝不能改】
 *   1. role 一律写成 STUDENT —— 绝不能让注册者自己选角色，
 *      否则任何人都能注册一个 TEACHER 账号，直接拿到后台权限。
 *   2. status 一律写成 PENDING —— 注册不能直接获得"能登录"的状态，
 *      必须经过管理员审核。
 *
 * 【为什么这个页面必须对游客开放】
 *   要注册的人本来就还没登录。所以 SecurityConfig 里放行了 /register。
 *
 * 【🔄 R-010 重构说明】
 *   `refill()` 原先收 13 个参数、被调用 7 次（加个字段要改 8 处）。
 *   现在改成传一个 `RegisterForm` 对象。
 *   **表单字段名、校验规则、提示文案、返回的 Model 属性名全都没变。**
 */
@Controller
public class RegisterController {

    /** 与服务端校验保持一致：新密码最短长度 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final StudentProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterController(UserRepository userRepository,
                              StudentProfileRepository profileRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 显示注册表单 */
    @GetMapping("/register")
    public String form(Model model) {
        model.addAttribute("allLevels", Level.values());   // 阶段下拉框
        return "register";
    }

    /**
     * 处理注册提交。
     *
     * 【为什么加 @Transactional】
     *   一次要写两张表（users 和 student_profiles）。
     *   如果第一张写成功、第二张失败了，就会留下一个"没有档案的账号"这种半成品。
     *   加上这个注解，两张表要么一起成功，要么一起回滚 —— 不会留下半成品。
     *
     * 【⚠️ 为什么还是逐个 `@RequestParam`，而不是 `@ModelAttribute RegisterForm`】
     *   见 `RegisterForm` 的类注释 —— 整体绑定会引入批量赋值漏洞。
     *   这里只**显式接收 13 个允许的字段**，然后装进 form 对象方便传递。
     */
    @PostMapping("/register")
    @Transactional
    public String submit(@RequestParam String realName,
                         @RequestParam String studentNo,
                         @RequestParam String username,
                         @RequestParam String phone,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         @RequestParam(required = false) String grade,
                         @RequestParam(required = false) String majorClass,
                         @RequestParam(required = false) String qq,
                         @RequestParam(required = false) String wechat,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String level,
                         Model model) {

        // 把 13 个字段装进一个对象，后面 refill 只传它
        RegisterForm form = new RegisterForm();
        form.setRealName(realName);
        form.setStudentNo(studentNo);
        form.setUsername(username);
        form.setPhone(phone);
        form.setGrade(grade);
        form.setMajorClass(majorClass);
        form.setQq(qq);
        form.setWechat(wechat);
        form.setEmail(email);
        form.setLanguages(languages);
        form.setLevel(level);

        // 先把所有输入去掉首尾空格 —— 从别处复制粘贴很容易带上空格
        realName = FormUtils.trimToEmpty(realName);
        studentNo = FormUtils.trimToEmpty(studentNo);
        username = FormUtils.trimToEmpty(username);
        phone = FormUtils.trimToEmpty(phone);

        // ---------- 校验（任何一条不过就带着错误回到表单）----------
        if (realName.isEmpty())    return refill(model, "请填写真实姓名", form);
        if (studentNo.isEmpty())   return refill(model, "请填写学号", form);
        if (username.isEmpty())    return refill(model, "请填写用户名", form);
        if (!phone.matches("1\\d{10}")) return refill(model, "手机号格式不对（应为 11 位、以 1 开头）", form);

        if (password == null || password.length() < MIN_PASSWORD_LENGTH)
            return refill(model, "密码至少 " + MIN_PASSWORD_LENGTH + " 位", form);
        if (!password.equals(confirmPassword))
            return refill(model, "两次输入的密码不一致", form);

        // ---------- 唯一性校验（复用 T-002 写的三个查询方法）----------
        // 数据库那三列本来就有 UNIQUE 约束，但提前查一遍能给出友好提示，
        // 而不是让用户看到一个 500 错误页。
        if (userRepository.findByUsername(username).isPresent())
            return refill(model, "这个用户名已经有人用了，换一个吧", form);
        if (userRepository.findByStudentNo(studentNo).isPresent())
            return refill(model, "这个学号已经注册过了", form);
        if (userRepository.findByPhone(phone).isPresent())
            return refill(model, "这个手机号已经注册过了", form);

        // ---------- 建账号 ----------
        User user = new User();
        user.setUsername(username);
        user.setStudentNo(studentNo);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));   // 存哈希，绝不存明文
        user.setRealName(realName);
        user.setRole(Role.STUDENT);            // 🔒 红线：注册者永远是学员
        user.setLogisticsDuty(false);
        user.setStatus(UserStatus.PENDING);    // 🔒 红线：注册后是"待审核"，登不进去
        userRepository.save(user);

        // ---------- 建档案 ----------
        // 因为用的是 IDENTITY 主键，save() 之后 id 已经被数据库回填，可以直接用
        StudentProfile profile = new StudentProfile();
        profile.setUserId(user.getId());
        profile.setGrade(FormUtils.blankToNull(grade));
        profile.setMajorClass(FormUtils.blankToNull(majorClass));
        profile.setQq(FormUtils.blankToNull(qq));
        profile.setWechat(FormUtils.blankToNull(wechat));
        profile.setEmail(FormUtils.blankToNull(email));
        profile.setLanguages(FormUtils.blankToNull(languages));
        profile.setLevel(FormUtils.parseEnum(Level.class, level));
        profile.setJoinDate(LocalDate.now());  // 入社日期就记注册当天
        profileRepository.save(profile);

        // 注册成功 → 回到注册页显示"已提交，等审核"
        return "redirect:/register?submitted";
    }

    /**
     * 校验失败：把填过的内容原样送回页面，免得用户重填一遍长表单。
     *
     * 【🔄 R-010】原先这里有 13 个参数，现在只收一个 form 对象。
     *   **塞进 Model 的属性名一个都没变** —— 所以 register.html 完全不用改。
     */
    private String refill(Model model, String error, RegisterForm form) {
        model.addAttribute("error", error);
        model.addAttribute("realName", form.getRealName());
        model.addAttribute("studentNo", form.getStudentNo());
        model.addAttribute("username", form.getUsername());
        model.addAttribute("phone", form.getPhone());
        model.addAttribute("grade", form.getGrade());
        model.addAttribute("majorClass", form.getMajorClass());
        model.addAttribute("qq", form.getQq());
        model.addAttribute("wechat", form.getWechat());
        model.addAttribute("email", form.getEmail());
        model.addAttribute("languages", form.getLanguages());
        model.addAttribute("level", form.getLevel());
        model.addAttribute("allLevels", Level.values());
        return "register";
    }
}
