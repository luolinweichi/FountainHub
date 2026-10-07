package com.fountainhub.controller;

import com.fountainhub.entity.AttendanceEvent;
import com.fountainhub.entity.AttendanceRecord;
import com.fountainhub.entity.AttendanceStatus;
import com.fountainhub.entity.CheckinType;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AttendanceEventRepository;
import com.fountainhub.repository.AttendanceRecordRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.service.AttendanceCalendarService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「我的考勤」（T-019）—— 学员自助签到 + 签到日历。
 *
 * ===========================================================================
 * 【"打卡"到底是什么（2026-10-03 项目负责人澄清）】
 *   打卡 = 「**你到机房了没有**」，**不是**"今天做了几道题"。
 *
 * 【页面上的内容】
 *  ① 顶部三个数字：今日 / 本周 / 累计（签到天数）
 *  ② ⭐ **签到日历**：只有两种颜色（已签到品牌蓝 / 未签到浅灰）
 *  ③ 底部：最长连续签到 / 最近一次签到 + 鼓励文案
 *  ④ 今天有活动就给【签到】按钮；下面还有「本周活动」明细表
 *
 * 🔄 2026-10-04 重构：上面的 ①②③ 已经搬进
 *   {@link AttendanceCalendarService#fillCalendarCard} ——
 *   因为项目负责人要求把「考勤」也放到 `/me`（我的主页）的主体位置，
 *   **两个页面要画同一张日历**，逻辑不能写两遍。
 *   本类现在只负责"这一页独有"的东西：**签到按钮**、**本周活动明细表**。
 *
 * 【⭐ 连续签到怎么算（2026-10-03 项目负责人定）】
 *   见 `AttendanceCalendarService` 的类注释 —— 规则只有一份，不在这里重复。
 *
 * 【三条规则】
 *   1. 只能签**当天**的活动；**当天任意时间都可以，不限几点**
 *   2. 签到后**不能自己取消**
 *   3. 🔒 只能签**自己** —— userId 从会话取，URL 里根本没有 userId
 * ===========================================================================
 */
@Controller
@RequestMapping("/me/attendance")
public class MyAttendanceController {

    private final AttendanceEventRepository eventRepository;
    private final AttendanceRecordRepository recordRepository;
    private final CurrentUserService currentUserService;
    private final AttendanceCalendarService attendanceCalendarService;

    public MyAttendanceController(AttendanceEventRepository eventRepository,
                                  AttendanceRecordRepository recordRepository,
                                  CurrentUserService currentUserService,
                                  AttendanceCalendarService attendanceCalendarService) {
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
        this.currentUserService = currentUserService;
        this.attendanceCalendarService = attendanceCalendarService;
    }

    @GetMapping
    public String page(@org.springframework.web.bind.annotation.RequestParam(required = false) Integer year,
                       Authentication authentication, Model model) {
        User me = currentUserService.require(authentication);
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // ---------- 签到日历卡片：三个数字 + 日历格子 + 连续周数 + 最近一次 + 鼓励 ----------
        // 🔒 userId 从会话取（me.getId()），**不是**从 URL 参数取。
        attendanceCalendarService.fillCalendarCard(model, me.getId(), year);

        // ---------- 本页独有：本周活动 + 「我签了没」 ----------
        // 【为什么要 recordByEvent】attendance_records 里只有 event_id，
        //   而模板要按"每场活动"判断我签了没 → 先做成 eventId → 记录 的映射。
        List<AttendanceRecord> myRecords = recordRepository.findByUserIdOrderByCheckinAtDesc(me.getId());
        Map<Long, AttendanceRecord> recordByEvent = new HashMap<>();
        for (AttendanceRecord r : myRecords) {
            recordByEvent.putIfAbsent(r.getEventId(), r);
        }
        List<AttendanceEvent> weekEvents = eventRepository.findByEventDateBetweenOrderByEventDateAsc(monday, sunday);

        model.addAttribute("today", today);
        model.addAttribute("weekEvents", weekEvents);
        model.addAttribute("recordByEvent", recordByEvent);
        return "me/attendance";
    }

    /**
     * 签到。
     *
     * 【为什么是 POST】签到会**改变数据**。用 GET 的话，浏览器预取、后退、
     * 或者别人发一个链接给你，都可能**莫名其妙多一条签到**。
     *
     * 【为什么路径里只有 eventId，没有 userId】🔒 userId 一律从会话取 ——
     * 这样根本**没有"帮别人签到"这个入口**，不是靠判断拦住的，而是压根不存在。
     */
    @PostMapping("/{eventId}")
    public String checkin(@PathVariable Long eventId,
                          Authentication authentication,
                          RedirectAttributes ra) {
        User me = currentUserService.require(authentication);

        AttendanceEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            ra.addFlashAttribute("error", "找不到这场考勤活动。");
            return "redirect:/me/attendance";
        }

        // 规则 1：只能签"当天"的活动。**当天几点都行**，不用再判时间。
        if (!LocalDate.now().equals(event.getEventDate())) {
            ra.addFlashAttribute("error", "只能签到**当天**的活动（补签请找社长）。");
            return "redirect:/me/attendance";
        }

        // 先查一遍：签过就不重复插入（表上还有 UNIQUE (event_id, user_id) 兜底）
        if (recordRepository.findByEventIdAndUserId(eventId, me.getId()).isPresent()) {
            ra.addFlashAttribute("error", "你今天已经签过到了。");
            return "redirect:/me/attendance";
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setEventId(eventId);
        record.setUserId(me.getId());
        record.setStatus(AttendanceStatus.PRESENT);   // 签到 = 到场
        record.setCheckinType(CheckinType.SELF);      // 自己签的（代打卡是 ADMIN，operatorId 才非空）
        try {
            recordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // 并发重复提交（比如说双击了按钮）：数据库的唯一约束会把第二条挡下来
            ra.addFlashAttribute("error", "你今天已经签过到了。");
            return "redirect:/me/attendance";
        }

        ra.addFlashAttribute("saved", "签到成功！");
        return "redirect:/me/attendance";
    }
}
