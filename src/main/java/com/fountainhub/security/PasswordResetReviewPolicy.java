package com.fountainhub.security;

import com.fountainhub.entity.Role;
import com.fountainhub.entity.User;

import java.util.Set;

/**
 * 「谁能审核谁的改密申请」—— 三层审核规则的【唯一来源】（T-012B 第二版，2026-10-07）。
 *
 * ===========================================================================
 * 【规矩（项目负责人 2026-10-07 决定）】
 *
 *   申请人的角色            谁能审核
 *   ---------------------   ----------------------------------------------
 *   学员 / 组长            指导老师 · 正社长 · 副社长
 *                          （⭐「副社长算社长」—— 项目负责人明确说的）
 *   正社长 / 副社长          **只有指导老师**（社长不能审社长）
 *   指导老师                **谁都审不了** → 只能由维护人员直接改数据库
 *                          （所以 ForgotPasswordController 会当场挡掉指导老师的申请，
 *                            免得留下一条永远没人能审的僵尸记录）
 *
 * ===========================================================================
 * 🔒【为什么写成静态工具类，而且两个 Controller 都必须调它】
 *
 *   这条规则**依赖数据**（要看"申请人是谁"），所以：
 *     · `@PreAuthorize` 表达不了 —— 它只能看**当前登录者**的角色，看不到申请记录里那个人 ❌
 *     · SecurityConfig 的 URL 规则也表达不了 —— 同理 ❌
 *   → 只能在**查到申请记录之后**用代码判。
 *
 *   而"判"这件事有**两个地方**要做：
 *     ① `AdminPasswordResetController` —— 真正执行审核时（**这才是安全边界**）
 *     ② `AdminStudentController`        —— 列表页决定「通过 / 拒绝」按钮显不显示
 *   两处各写一遍 = 迟早写歪（界面放了按钮后端不让点，或者反过来）。
 *   ✅ 所以规则抽到这里，**两边调同一个方法**。
 *
 *   ⚠️ 界面藏按钮只是"防误操作"；**真正挡住越权的是 ①**。
 *      别以为藏了按钮就安全 —— 别人照样能直接 POST 那个审核地址。
 *      所以 reject 里也判了一次：否则社长能拿「拒绝」去骚扰他审不了的申请。
 * ===========================================================================
 */
public final class PasswordResetReviewPolicy {

    /** 能审「学员 / 组长」的申请：指导老师 + 正社长 + 副社长 */
    private static final Set<Role> FOR_STUDENT =
            Set.of(Role.TEACHER, Role.PRESIDENT, Role.VICE_PRESIDENT);

    /** 能审「正社长 / 副社长」的申请：只有指导老师 */
    private static final Set<Role> FOR_LEADERSHIP = Set.of(Role.TEACHER);

    /** 工具类，不许 new */
    private PasswordResetReviewPolicy() {
    }

    /**
     * 当前审核人能不能审这条申请。
     *
     * @param reviewer  当前登录、正在审核的人（null = 不能审）
     * @param applicant 申请记录里那个人（null = 人可能已经被删了，也不能审）
     */
    public static boolean canReview(User reviewer, User applicant) {
        if (reviewer == null || applicant == null) {
            return false;
        }
        Role applicantRole = applicant.getRole();
        if (applicantRole == null) {
            return false;
        }

        // ① 指导老师的申请：页面上一律审不了（只能由维护人员改数据库）
        if (applicantRole == Role.TEACHER) {
            return false;
        }
        // ② 正社长 / 副社长的申请：只有指导老师能审
        if (applicantRole == Role.PRESIDENT || applicantRole == Role.VICE_PRESIDENT) {
            return FOR_LEADERSHIP.contains(reviewer.getRole());
        }
        // ③ 其余（学员 / 组长）的申请：指导老师、正社长、副社长都能审
        //    ⚠️ 组长不靠 users.role 存（见 Role.assignable()），组长的 role 就是 STUDENT → 天然走这一条 ✅
        return FOR_STUDENT.contains(reviewer.getRole());
    }

    /** 这条申请是不是"页面根本审不了"（= 申请人本身是指导老师）—— 列表页据此显示不同提示 */
    public static boolean isSqlOnly(User applicant) {
        return applicant != null && applicant.getRole() == Role.TEACHER;
    }

    /**
     * 被拒绝时给页面用的一句话原因。
     * 【注意】这只是给**界面**看的；真正的拒绝是抛 `AccessDeniedException` → 403 页面。
     */
    public static String denyReason(User applicant) {
        if (applicant == null) {
            return "申请人已经不在系统里了。";
        }
        if (applicant.getRole() == Role.TEACHER) {
            return "指导老师的改密申请不能在这里审核，需要由维护人员直接处理。";
        }
        return "只有指导老师能审核社长 / 副社长的改密申请。";
    }
}
