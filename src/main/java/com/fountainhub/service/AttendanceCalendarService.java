package com.fountainhub.service;

import com.fountainhub.entity.AttendanceEvent;
import com.fountainhub.entity.AttendanceRecord;
import com.fountainhub.entity.AttendanceType;
import com.fountainhub.repository.AttendanceEventRepository;
import com.fountainhub.repository.AttendanceRecordRepository;
import com.fountainhub.util.CalendarGrid;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 签到日历的数据装配（T-019 抽出；2026-10-04 新增本类）。
 *
 * ===========================================================================
 * 【为什么要有这个类】
 *   这张签到日历原来只出现在 `/me/attendance` 一个页面上。
 *   2026-10-04 项目负责人决定：**撤掉「学习热力图」（题库二期才有数据，其他社团用不上），
 *   把「考勤」放到 `/me`（我的主页）的主体位置** ——
 *   于是 **`/me` 与 `/me/attendance` 两个页面都要画同一张签到日历**。
 *
 *   与其把那 80 行计算逻辑**复制两遍**（以后改一处、漏一处），不如收进这一个类。
 *   类比：和 `GlobalModelAdvice` 把"全站公共数据"收口到一处是同一个思路 ——
 *   **同一份规则只允许存在一份**。
 *
 * 【它的职责边界】
 *   ✅ 只做"**把数据塞进 Model**"这件事：查数据、算颜色、填模型。
 *   ❌ 不处理 HTTP、不返回视图名、不碰签到按钮 —— 那些仍归各自的 Controller。
 *   所以两个 Controller 都能用它，分层仍然是 Controller → Service → Repository。
 *
 * 🔒🔒【本类必须"无状态" —— 这是一条硬规则，不是风格偏好】
 *   本类被 Spring 注册成 **单例（singleton）**：整个应用只有**一个**实例，
 *   所有用户的请求**共用**它。所以：
 *     ✅ 用户 id 一律**走方法参数**传进来；
 *     ❌ **绝对不许**把"当前是谁"存进成员变量。
 *   反面教材（写这段代码时我自己差点犯的错）：如果图省事加一个
 *     `private Long currentUserId;` + `useUser(id)`，
 *   那么两个用户同时打开页面时，**后一个请求会把 id 覆盖掉** ——
 *   结果就是 **A 看到 B 的考勤数据**。这类"把请求级数据塞进单例字段"
 *   是后端最常见的安全事故之一，且**本地单人测试时永远测不出来**。
 *   ⇒ 本类的所有成员变量**只能是依赖（Repository）和常量**。
 *
 * 【⭐ 连续签到怎么算（2026-10-03 项目负责人定，规则不变）】
 *   **只算「例行活动」**：这例行活动日来了 + 下例行活动日也来了 = 连续 2 周。
 *   平时（日常开放）来不来都不影响连续；某周【没有创建】例行活动则跳过、不算断。
 * ===========================================================================
 */
@Service
public class AttendanceCalendarService {

    /** 已签到 = 品牌蓝（docs/08 §3.3 冻结的色值，**不得改**） */
    public static final String COLOR_SIGNED = "#145AFF";
    /** 未签到 = 浅灰（同样是冻结色值） */
    public static final String COLOR_ABSENT = "#DDE3E7";

    /** 7 行格子的行名：周一到周日（两个页面共用同一份，避免各写一遍写歪） */
    public static final List<String> WEEKDAY_LABELS =
            List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ⚠️ 成员变量只能是"注入进来的依赖"，不许放任何"跟某个用户/某次请求有关"的东西（见类注释）
    private final AttendanceEventRepository eventRepository;
    private final AttendanceRecordRepository recordRepository;

    public AttendanceCalendarService(AttendanceEventRepository eventRepository,
                                     AttendanceRecordRepository recordRepository) {
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
    }

    /**
     * 把「签到日历卡片」所需的**全部**模型数据塞进 Model（`/me` 与 `/me/attendance` 共用）。
     *
     * ⚠️ **年份左右箭头的链接不在这里** —— `/me` 与 `/me/attendance` 的链接地址不同，
     *    各自写在自己的模板里（这是唯一一处刻意保留的重复）。
     *
     * @param userId 当前登录用户 id —— **必须由调用方从会话取**（🔒 绝不能来自 URL 参数）
     * @param year   用户点年份箭头带来的年份；null = 今年
     */
    public void fillCalendarCard(Model model, Long userId, Integer year) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // 我签过到的所有「天」（一次查询，下面 ①~⑤ 都用它）
        Set<LocalDate> signedDates = signedDates(userId);

        // ---------- ① 三个数字 ----------
        model.addAttribute("todaySigned", signedDates.contains(today));
        int weekDays = 0;
        for (LocalDate d : signedDates) {
            if (!d.isBefore(monday) && !d.isAfter(sunday)) {
                weekDays++;
            }
        }
        model.addAttribute("weekDays", weekDays);
        model.addAttribute("totalDays", signedDates.size());

        // ---------- ② 日历格子：⭐ 服务端直接算好每天的颜色，吐 HTML 格子 ----------
        // 【为什么不用 ECharts】见 CalendarGrid 顶部注释：CDN/JS 一旦出问题就是一片空白，
        //   而开发沙箱没外网、没法调试浏览器里的 JS。服务端渲染永远不会白。
        int selectedYear = (year == null) ? today.getYear() : year;
        model.addAttribute("calendarRows", CalendarGrid.rows(
                selectedYear,
                d -> signedDates.contains(d) ? COLOR_SIGNED : COLOR_ABSENT,
                d -> signedDates.contains(d) ? "：已签到" : "：未签到"));
        model.addAttribute("monthSpans", CalendarGrid.monthSpans(selectedYear));
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("prevYear", selectedYear - 1);
        model.addAttribute("nextYear", selectedYear + 1);
        model.addAttribute("weekdayLabels", WEEKDAY_LABELS);

        // ---------- ③ 连续来了几周「例行活动」----------
        int weekStreak = weekStreak(signedDates, today);
        model.addAttribute("weekStreak", weekStreak);

        // ---------- ④ 最近一次签到 ----------
        model.addAttribute("lastCheckin", lastCheckinText(userId));

        // ---------- ⑤ 鼓励文案 + 本周全勤 ----------
        model.addAttribute("encourage", encourage(weekStreak));
        model.addAttribute("everyDayThisWeek", everyDayThisWeek(signedDates, monday, sunday, today));
    }

    /**
     * 我签过到的是哪些「天」。
     *
     * 【为什么这么绕】`attendance_records` 里只有 event_id，**没有日期**；
     *   日期在 `attendance_events.event_date` 上。所以必须先查记录、再查活动。
     */
    public Set<LocalDate> signedDates(Long userId) {
        List<AttendanceRecord> myRecords = recordRepository.findByUserIdOrderByCheckinAtDesc(userId);

        Set<Long> eventIds = new HashSet<>();
        for (AttendanceRecord r : myRecords) {
            eventIds.add(r.getEventId());
        }
        Map<Long, LocalDate> dateByEvent = new HashMap<>();
        if (!eventIds.isEmpty()) {
            for (AttendanceEvent e : eventRepository.findAllById(eventIds)) {
                dateByEvent.put(e.getId(), e.getEventDate());
            }
        }
        Set<LocalDate> signedDates = new HashSet<>();
        for (AttendanceRecord r : myRecords) {
            LocalDate d = dateByEvent.get(r.getEventId());
            if (d != null) {
                signedDates.add(d);
            }
        }
        return signedDates;
    }

    /** 连续签到周数（从最近的例行活动日往回数；还没到的例行活动日不算，没创建过例会的周跳过） */
    public int weekStreak(Set<LocalDate> signedDates, LocalDate today) {
        List<AttendanceEvent> weeklyEvents =
                eventRepository.findByTypeOrderByEventDateAsc(AttendanceType.WEEKLY_MEETING);
        int weekStreak = 0;
        for (int i = weeklyEvents.size() - 1; i >= 0; i--) {
            LocalDate d = weeklyEvents.get(i).getEventDate();
            if (d.isAfter(today)) {
                continue;   // 还没到的例行活动日不算（不然"未来没来"会把连续打断）
            }
            if (signedDates.contains(d)) {
                weekStreak++;
            } else {
                break;      // 有一次没来 → 连续到此为止
            }
        }
        return weekStreak;
    }

    /** 鼓励文案（放后端就是"一处规则"，将来改文案只改这里，不用翻模板） */
    public String encourage(int weekStreak) {
        if (weekStreak >= 5) {
            return "连续 " + weekStreak + " 周全勤，很了不起 🔥";
        }
        if (weekStreak >= 3) {
            return "连续 " + weekStreak + " 周没缺过，稳！";
        }
        if (weekStreak >= 1) {
            return "好的开始 👏 继续保持";
        }
        return "这周重新开始，来吧 💪";
    }

    /** 本周【有活动的日子】他是不是天天都来了（用于额外夸一句） */
    public boolean everyDayThisWeek(Set<LocalDate> signedDates, LocalDate monday, LocalDate sunday, LocalDate today) {
        List<AttendanceEvent> weekEvents =
                eventRepository.findByEventDateBetweenOrderByEventDateAsc(monday, sunday);
        if (weekEvents.isEmpty()) {
            return false;
        }
        for (AttendanceEvent e : weekEvents) {
            if (e.getEventDate().isAfter(today) || !signedDates.contains(e.getEventDate())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 最近一次签到的时间文本。
     * 【为什么要再查一次记录表】日期集合（signedDates）里**没有时间戳** ——
     *   要显示"最近一次签到：2026-10-04 16:30"就必须拿到记录本身。
     *   代价是一条走 user_id 索引的查询，可以接受。
     */
    private String lastCheckinText(Long userId) {
        for (AttendanceRecord r : recordRepository.findByUserIdOrderByCheckinAtDesc(userId)) {
            if (r.getCheckinAt() != null) {
                return r.getCheckinAt().format(STAMP);   // 已按时间倒序，第一条就是最近
            }
        }
        return "还没有签到过";
    }
}
