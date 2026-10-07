package com.fountainhub.controller;

import com.fountainhub.entity.AttendanceEvent;
import com.fountainhub.entity.AttendanceRecord;
import com.fountainhub.entity.AttendanceStatus;
import com.fountainhub.entity.AttendanceType;
import com.fountainhub.entity.CheckinType;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AttendanceEventRepository;
import com.fountainhub.repository.AttendanceRecordRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.util.FormUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台考勤管理（T-020）—— 创建活动 + 代打卡 + 出勤率。
 *
 * ===========================================================================
 * 【权限（2026-10-03 项目负责人定）】
 *   指导老师 / 正社长 / 副社长 ✅（与权限矩阵一致）
 *   **组长 🚫 一律 403** —— 原因：**代打卡 = 能写别人的数据**，
 *     是权限最重的一类操作，与「组长不能改任何数据」（T-017）冲突。
 *
 *   两道锁：类上的 @PreAuthorize（服务器层）+ 模板里的 sec:authorize（页面层）。
 *   ⚠️ 页面层只是"看不见"，**真正拦住的是服务器层** —— 所以负向测试要直接发 POST 验证。
 *
 * 【⭐ 代打卡为什么必须记 operator_id】
 *   需求原话：「社长代打卡（手动记录），并**记录是谁代打的、何时打的**」。
 *   考勤是要交给指导老师的凭据，必须能追溯到人。
 *   所以：
 *     checkinType = ADMIN  → 这条不是本人签的
 *     operatorId  = 代打的那个人
 *     userId      = 被代打的人（⚠️ 这是**唯一**能写别人考勤的入口）
 *   「何时打的」由数据库的 checkin_at 自动记（实体里是 insertable=false）。
 *
 * 【「没去就空着」原则继续守住】
 *   代打卡只做【到场】和【请假】两种，**不会**批量生成"缺席"记录 ——
 *   没来的人在数据库里就是没有任何记录，只是出勤率的分子少一次。
 * ===========================================================================
 */
@Controller
@RequestMapping("/admin/attendance")
@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
public class AdminAttendanceController {

    private final AttendanceEventRepository eventRepository;
    private final AttendanceRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AdminAttendanceController(AttendanceEventRepository eventRepository,
                                     AttendanceRecordRepository recordRepository,
                                     UserRepository userRepository,
                                     CurrentUserService currentUserService) {
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    // ==================== ① 活动列表 + 创建 + 出勤率 ====================
    @GetMapping
    public String list(Model model) {
        List<AttendanceEvent> events = eventRepository.findAllByOrderByEventDateDesc();
        List<User> students = students();
        List<AttendanceRecord> all = recordRepository.findAll();

        // 一场活动有几个人【到场】
        Map<Long, Integer> presentByEvent = new HashMap<>();
        // 一个人【到场】过几次
        Map<Long, Integer> presentByUser = new HashMap<>();
        Set<String> userEventPairs = new HashSet<>();   // 防重复计数
        for (AttendanceRecord r : all) {
            if (r.getStatus() == AttendanceStatus.PRESENT) {
                presentByEvent.merge(r.getEventId(), 1, Integer::sum);
                if (userEventPairs.add(r.getEventId() + ":" + r.getUserId())) {
                    presentByUser.merge(r.getUserId(), 1, Integer::sum);
                }
            }
        }

        // 出勤率 = 到场场次 ÷ 应到场次（应到 = 全部活动场次）
        int totalEvents = events.size();
        List<Map<String, Object>> rates = new ArrayList<>();
        for (User s : students) {
            int present = presentByUser.getOrDefault(s.getId(), 0);
            Map<String, Object> row = new HashMap<>();
            row.put("name", s.getRealName());
            row.put("studentNo", s.getStudentNo());
            row.put("present", present);
            row.put("should", totalEvents);
            row.put("percent", totalEvents == 0 ? 0.0 : Math.round(present * 1000.0 / totalEvents) / 10.0);
            rates.add(row);
        }

        model.addAttribute("events", events);
        model.addAttribute("presentByEvent", presentByEvent);
        model.addAttribute("rates", rates);
        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("studentCount", students.size());
        return "admin/attendance";
    }

    // ==================== ② 创建活动 ====================
    @PostMapping("/events")
    public String createEvent(@RequestParam String eventDate,
                              @RequestParam String type,
                              @RequestParam(required = false) String title,
                              @RequestParam(required = false) String description,
                              Authentication authentication,
                              RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        LocalDate date = FormUtils.parseDate(eventDate);
        AttendanceType t = FormUtils.parseEnum(AttendanceType.class, type);
        if (date == null || t == null) {
            ra.addFlashAttribute("error", "日期或类型不合法。");
            return "redirect:/admin/attendance";
        }

        // 表上有 UNIQUE (event_date, type) —— 先查一遍，给一句人话而不是 500
        if (eventRepository.findByEventDateAndType(date, t).isPresent()) {
            ra.addFlashAttribute("error", "「" + date + "」已经有一场" + t.getLabel() + "了（同一天同类型只能一场）。");
            return "redirect:/admin/attendance";
        }

        AttendanceEvent e = new AttendanceEvent();
        e.setEventDate(date);
        e.setType(t);
        e.setTitle(FormUtils.blankToNull(title) == null ? t.getLabel() : FormUtils.trimToEmpty(title));
        e.setDescription(FormUtils.blankToNull(description));
        e.setCreatedBy(me.getId());
        try {
            eventRepository.save(e);
        } catch (DataIntegrityViolationException ex) {
            // 并发（比如双击）：唯一约束兜底
            ra.addFlashAttribute("error", "「" + date + "」已经有一场" + t.getLabel() + "了。");
            return "redirect:/admin/attendance";
        }
        ra.addFlashAttribute("saved", "已创建：" + date + " " + t.getLabel());
        return "redirect:/admin/attendance";
    }

    // ==================== ③ 单场明细 + 代打卡 ====================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AttendanceEvent event = eventRepository.findById(id).orElse(null);
        if (event == null) {
            return "redirect:/admin/attendance";
        }
        List<User> students = students();
        Map<Long, AttendanceRecord> byUser = new HashMap<>();
        for (AttendanceRecord r : recordRepository.findByEventId(id)) {
            byUser.putIfAbsent(r.getUserId(), r);   // 唯一约束保证不会有两份
        }
        // 「是谁代打的」要显示人名 —— 一次把操作人查出来做成 Map，避免逐条查库
        Set<Long> operatorIds = new HashSet<>();
        for (AttendanceRecord r : byUser.values()) {
            if (r.getOperatorId() != null) {
                operatorIds.add(r.getOperatorId());
            }
        }
        Map<Long, String> operatorNames = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            for (User u : userRepository.findAllById(operatorIds)) {
                operatorNames.put(u.getId(), u.getRealName());
            }
        }
        model.addAttribute("event", event);
        model.addAttribute("students", students);
        model.addAttribute("byUser", byUser);
        model.addAttribute("operatorNames", operatorNames);
        return "admin/attendance-detail";
    }

    /**
     * 代打卡 / 标请假。
     *
     * @param status PRESENT（到场，代打卡）或 LEAVE（请假）；**不提供 ABSENT** ——
     *               没来就是没有记录，这是需求「没去就空着」的字面实现。
     */
    @PostMapping("/{id}/mark")
    public String mark(@PathVariable Long id,
                       @RequestParam Long userId,
                       @RequestParam String status,
                       Authentication authentication,
                       RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        AttendanceEvent event = eventRepository.findById(id).orElse(null);
        if (event == null) {
            ra.addFlashAttribute("error", "找不到这场考勤活动。");
            return "redirect:/admin/attendance";
        }
        User target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            ra.addFlashAttribute("error", "找不到这个学员。");
            return "redirect:/admin/attendance/" + id;
        }
        AttendanceStatus st = FormUtils.parseEnum(AttendanceStatus.class, status);
        if (st != AttendanceStatus.PRESENT) {
            st = AttendanceStatus.LEAVE;   // 只允许两种，别的一律当请假处理（不做"缺席"）
        }

        AttendanceRecord existing = recordRepository.findByEventIdAndUserId(id, userId).orElse(null);
        if (existing != null) {
            // 已经签过/记过了 —— 不新增，改为更新状态并记下是谁改的
            existing.setStatus(st);
            existing.setCheckinType(CheckinType.ADMIN);
            existing.setOperatorId(me.getId());
            recordRepository.save(existing);
            ra.addFlashAttribute("saved", "已更新 " + target.getRealName() + " 的考勤为「" + st.getLabel() + "」，操作人：" + me.getRealName());
            return "redirect:/admin/attendance/" + id;
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setEventId(id);
        record.setUserId(userId);
        record.setStatus(st);
        record.setCheckinType(CheckinType.ADMIN);   // ⭐ 不是本人签的
        record.setOperatorId(me.getId());           // ⭐ 记录是谁代打的
        try {
            recordRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            ra.addFlashAttribute("error", target.getRealName() + " 这场已经有记录了。");
            return "redirect:/admin/attendance/" + id;
        }
        ra.addFlashAttribute("saved", "已为 " + target.getRealName() + " 记录「" + st.getLabel() + "」，操作人：" + me.getRealName());
        return "redirect:/admin/attendance/" + id;
    }

    /** 全部学员（role = STUDENT），按姓名排序 */
    private List<User> students() {
        List<User> out = new ArrayList<>();
        for (User u : userRepository.findAllByOrderByRealNameAsc()) {
            if (u.getRole() == Role.STUDENT) {
                out.add(u);
            }
        }
        return out;
    }
}
