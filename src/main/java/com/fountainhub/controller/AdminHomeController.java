package com.fountainhub.controller;

import com.fountainhub.entity.AttendanceRecord;
import com.fountainhub.entity.AttendanceStatus;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.StudyGroup;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AttendanceEventRepository;
import com.fountainhub.repository.AttendanceRecordRepository;
import com.fountainhub.repository.AwardRepository;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台总览（T-022 / FR-11.1）—— 社长一进来就看到全社的情况。
 *
 * ===========================================================================
 * 【权限】指导老师 / 正社长 / 副社长（和 rest of /admin 一致）。组长一律 403 ——
 *   他只能看本组的姓名与人数（T-017 → /me/group）。
 *
 * 🔄 【2026-10-04 删掉了「本周做题」与「做题排行榜」】
 *   原来这里有：
 *     · KPI「本周做题」（基于 checkins 表）
 *     · 「做题排行榜」（FR-11.2，同样基于 checkins）
 *   项目负责人决定**撤掉整个"学习"功能**（题库是二期才有数据，其他社团用不上），
 *   这两处是同一个功能的另一半 → 一并删除。
 *   连带删除的还有：`checkins` 表、`Checkin` / `CheckinMetric` / `CheckinSource` 实体、
 *   `CheckinRepository` —— 全都只在服务这一个功能。
 *   ⚠️ `CheckinType`（注意不是 Source）**保留** —— 它是**考勤**在用的
 *      （`attendance_records.checkin_type` 表示"本人签到 / 社长代打卡"），与学习无关。
 *
 *   ⇒ KPI 由 5 个变 4 个：在社学员 · 本周签到 · 考勤活动 · 获奖记录。
 * ===========================================================================
 */
@Controller
public class AdminHomeController {

    private final UserRepository userRepository;
    private final StudyGroupRepository groupRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceEventRepository eventRepository;
    private final AwardRepository awardRepository;

    public AdminHomeController(UserRepository userRepository,
                               StudyGroupRepository groupRepository,
                               AttendanceRecordRepository recordRepository,
                               AttendanceEventRepository eventRepository,
                               AwardRepository awardRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.recordRepository = recordRepository;
        this.eventRepository = eventRepository;
        this.awardRepository = awardRepository;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public String overview(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);

        // ---------- 人数 ----------
        List<User> students = new ArrayList<>();
        for (User u : userRepository.findAllByOrderByRealNameAsc()) {
            if (u.getRole() == Role.STUDENT) {
                students.add(u);
            }
        }
        model.addAttribute("studentCount", students.size());

        // ---------- 各组人数 ----------
        List<StudyGroup> groups = groupRepository.findAllByOrderBySortOrderAsc();
        Map<Long, Integer> countByGroup = new HashMap<>();
        int ungrouped = 0;
        for (User u : students) {
            if (u.getGroupId() == null) {
                ungrouped++;
            } else {
                countByGroup.merge(u.getGroupId(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> groupRows = new ArrayList<>();
        for (StudyGroup g : groups) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", g.getName());
            row.put("count", countByGroup.getOrDefault(g.getId(), 0));
            groupRows.add(row);
        }
        model.addAttribute("groupRows", groupRows);
        model.addAttribute("ungrouped", ungrouped);

        // ---------- 本周签到次数 ----------
        int weekCheckins = 0;
        for (AttendanceRecord r : recordRepository.findAll()) {
            if (r.getStatus() == AttendanceStatus.PRESENT
                    && r.getEventId() != null
                    && eventRepository.findById(r.getEventId()).isPresent()
                    && !eventRepository.findById(r.getEventId()).get().getEventDate().isBefore(monday)
                    && !eventRepository.findById(r.getEventId()).get().getEventDate().isAfter(today)) {
                weekCheckins++;
            }
        }
        model.addAttribute("weekCheckins", weekCheckins);
        model.addAttribute("eventCount", eventRepository.count());
        // T-032：获奖记录条数（原来这里写死"待排期"，做完获奖管理就得显示真实数字）
        model.addAttribute("awardCount", awardRepository.count());
        model.addAttribute("today", today);
        return "admin/index";
    }
}
