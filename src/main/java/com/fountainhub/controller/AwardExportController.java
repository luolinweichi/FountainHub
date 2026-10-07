package com.fountainhub.controller;

import com.fountainhub.entity.Award;
import com.fountainhub.entity.AwardStudent;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AwardRepository;
import com.fountainhub.repository.AwardStudentRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.CsvFile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 获奖清单导出（T-036 / FR-11.3）—— 学校 / 指导老师要「社团拿了哪些奖」时用。
 *
 * ===========================================================================
 * 【接口】GET /admin/export/awards  → 一个 CSV 文件（一条奖一行，团队奖把成员并在一格里）
 * 【权限】指导老师 / 正社长 / 副社长（同其它后台导出）。组长 403。
 * 🔒 【隐私】不含手机号 / QQ / 微信 / 邮箱；只有**姓名**。
 *
 * ⚠️⚠️ 【这里和"公开意愿"的关系，必须说清楚】
 *   红线是「获奖**对外展示**必须经学员**本人**同意」—— 它约束的是**公开页面**
 *   （`/awards` 展示墙、首页「获奖公示」），那两处由 `PublicAwardService` 统一过滤，
 *   **只带 `is_public=1` 的人、整条奖没人同意就整条不出现**。
 *   而本导出是**后台功能**（只有社长层/老师能下载，用于向学校报送材料），
 *   所以**两条都导**，但额外给出「同意公开人数 / 总人数」这一列 ——
 *   拿到表的人一眼就知道**哪些内容可以对外发、哪些不能**。
 *   （若项目负责人认为"交给学校"也算对外，再把这里改成只导同意者即可，改动很小。）
 * ===========================================================================
 */
@Controller
@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
public class AwardExportController {

    private final AwardRepository awardRepository;
    private final AwardStudentRepository linkRepository;
    private final UserRepository userRepository;

    public AwardExportController(AwardRepository awardRepository,
                                 AwardStudentRepository linkRepository,
                                 UserRepository userRepository) {
        this.awardRepository = awardRepository;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/export/awards")
    public ResponseEntity<byte[]> exportAwards() {
        // 人名：id → 姓名（一次读完，循环里不查库）
        Map<Long, String> nameById = new HashMap<>();
        for (User u : userRepository.findAll()) {
            nameById.put(u.getId(), u.getRealName());
        }
        // 关联行：awardId → 该奖的所有成员（保持库里的顺序）
        Map<Long, List<AwardStudent>> linksByAward = new LinkedHashMap<>();
        for (AwardStudent link : linkRepository.findAll()) {
            linksByAward.computeIfAbsent(link.getAwardId(), k -> new ArrayList<>()).add(link);
        }

        List<List<String>> table = new ArrayList<>();
        table.add(List.of("比赛名称", "级别", "奖项等级", "获奖日期", "举办方", "指导老师",
                "获奖学员", "同意公开人数", "总人数"));

        for (Award a : awardRepository.findAllByOrderByAwardDateDescIdDesc()) {
            List<AwardStudent> links = linksByAward.getOrDefault(a.getId(), List.of());

            StringBuilder names = new StringBuilder();
            int publicCount = 0;
            for (AwardStudent link : links) {
                if (names.length() > 0) {
                    names.append('、');   // 用顿号分隔姓名，避免 CSV 里再多一层引号
                }
                names.append(nameById.getOrDefault(link.getUserId(), "（已删除用户）"));
                if (Boolean.TRUE.equals(link.getIsPublic())) {
                    publicCount++;
                }
            }

            table.add(List.of(
                    CsvFile.text(a.getCompetitionName()),
                    a.getAwardLevel() == null ? "" : a.getAwardLevel().getLabel(),
                    a.getPrize() == null ? "" : a.getPrize().getLabel(),
                    CsvFile.text(a.getAwardDate()),
                    CsvFile.text(a.getOrganizer()),
                    CsvFile.text(a.getAdvisorName()),
                    names.toString(),
                    String.valueOf(publicCount),
                    String.valueOf(links.size())
            ));
        }
        return CsvFile.download("获奖清单", "awards", table);
    }
}
