package com.fountainhub.controller;

import com.fountainhub.entity.Role;
import com.fountainhub.entity.StudentProfile;
import com.fountainhub.entity.StudyGroup;
import com.fountainhub.entity.User;
import com.fountainhub.repository.StudentProfileRepository;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.CsvFile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学员名单导出（T-036 / FR-11.3）—— 给学校 / 指导老师要名单时用。
 *
 * ===========================================================================
 * 【接口】GET /admin/export/students  → 一个 CSV 文件
 *
 * 【权限】指导老师 / 正社长 / 副社长（与其它后台导出一致）。**组长一律 403** ——
 *   组长只能看本组的姓名与人数（T-017），导出全社名单不属于他的权限。
 *
 * 🔒🔒 【隐私红线 —— 本类最重要的一条】
 *   名单里**绝不出现手机号**（FR-11.3 硬性要求，也是 docs/spec.md §3.3 的红线）；
 *   同理**不导** QQ / 微信 / 邮箱（`student_profiles` 里那几个隐私列一律不碰）。
 *   导出的列只有：姓名 / 学号 / 组别 / 状态 / 年级 / 专业班级 / 入社日期 / 擅长语言 / 阶段。
 *
 * 【为什么要带「状态」列】
 *   注册是「学员自助注册 + 管理员审核」，所以库里会同时存在**待审核（PENDING）**的人。
 *   需求：**全部学员都导出，但带状态列**，看的人自己分辨谁是正式成员
 *   （比"只导在读"更安全：不会因为筛选条件把刚注册的人悄悄漏掉）。
 *
 * 【导出的是 CSV 不是 .xlsx】原因见 {@link CsvFile} 的类注释（沙箱无外网、加不上 POI）。
 * ===========================================================================
 */
@Controller
@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
public class StudentExportController {

    private final UserRepository userRepository;
    private final StudentProfileRepository profileRepository;
    private final StudyGroupRepository groupRepository;

    public StudentExportController(UserRepository userRepository,
                                   StudentProfileRepository profileRepository,
                                   StudyGroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.groupRepository = groupRepository;
    }

    @GetMapping("/admin/export/students")
    public ResponseEntity<byte[]> exportStudents() {
        // ---------- 先把两张「字典表」读进内存 ----------
        // 【为什么一次读完】人数很少（几十人），一次读完在内存里拼，
        //   比在循环里逐条查库（N+1）快得多，代码也更直白。
        Map<Long, String> groupNameById = new HashMap<>();
        for (StudyGroup g : groupRepository.findAllByOrderBySortOrderAsc()) {
            groupNameById.put(g.getId(), g.getName());
        }
        Map<Long, StudentProfile> profileByUserId = new HashMap<>();
        for (StudentProfile p : profileRepository.findAll()) {
            profileByUserId.put(p.getUserId(), p);
        }

        // ---------- 表头 ----------
        List<List<String>> table = new ArrayList<>();
        table.add(List.of("姓名", "学号", "组别", "状态", "年级", "专业班级", "入社日期", "擅长语言", "阶段"));

        // ---------- 一名学员一行 ----------
        for (User u : userRepository.findAllByOrderByRealNameAsc()) {
            if (u.getRole() != Role.STUDENT) {
                continue;   // 只导学员；老师/社长不在这份名单里（角色另有归属）
            }
            StudentProfile p = profileByUserId.get(u.getId());
            table.add(List.of(
                    CsvFile.text(u.getRealName()),                                    // 🔒 姓名
                    CsvFile.text(u.getStudentNo()),                                   // 学号
                    u.getGroupId() == null ? "未分组" : CsvFile.text(groupNameById.get(u.getGroupId())),
                    u.getStatus() == null ? "" : u.getStatus().getLabel(),            // 待审核 / 在读 / 退社 / 毕业 / 已禁用
                    p == null ? "" : CsvFile.text(p.getGrade()),                      // 年级
                    p == null ? "" : CsvFile.text(p.getMajorClass()),                 // 专业班级
                    p == null ? "" : CsvFile.text(p.getJoinDate()),                   // 入社日期（LocalDate → ISO，Excel 能排序）
                    p == null ? "" : CsvFile.text(p.getLanguages()),                  // 擅长语言
                    p == null || p.getLevel() == null ? "" : p.getLevel().getLabel()  // 入门 / 进阶 / 竞赛
            ));
        }
        return CsvFile.download("学员名单", "students", table);
    }
}
