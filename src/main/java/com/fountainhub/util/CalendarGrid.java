package com.fountainhub.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 日历格子的排版工具（T-019 / T-021）。
 *
 * 🔄 2026-10-04：原来它是【签到日历】与【学习热力图】**共用**的；
 *    项目负责人决定撤掉学习热力图（题库二期才有数据、其他社团用不上）之后，
 *    **现在只有签到日历用它**（调用方是 `service/AttendanceCalendarService`）。
 *    ⭐ **本类一行代码都没改** —— 这正是当初"两个图共用一套排版"的红利：
 *    撤掉一个功能时，工具类原地不动。
 *
 * ===========================================================================
 * 【为什么要服务端渲染，而不是用 ECharts】
 *   一开始这两个图是用 ECharts 画的（好看、现成）。但实测发现两个问题：
 *     ① 颜色太淡，没数据的格子在白卡片上几乎看不见；
 *     ② **项目负责人的浏览器里整个图压根没画出来**（空白），
 *        而 AI 的开发沙箱没有外网，**根本没法调试浏览器里的 JS / CDN**。
 *   所以改成**后端算好颜色、直接吐 HTML 格子** ——
 *     ✅ 不依赖 CDN、不依赖 JS → **永远不会再出现"空白"**
 *     ✅ 颜色完全由后端控制 → 想多明显就多明显
 *     ✅ 也符合本项目"服务端渲染"的一贯做法
 *
 * 【排版规则（和 GitHub 那种图一样）】
 *   一共 7 行：周一 ~ 周日；
 *   每一列 = 一周；从左到右按时间推进。
 *   所以只要按日期顺序把格子一条条吐出来，外面用 CSS 排成 7 行一列即可。
 * ===========================================================================
 */
public final class CalendarGrid {

    private CalendarGrid() {
        // 工具类，不允许实例化
    }

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 生成 7 行格子（周一~周日）。
     *
     * @param year       要画哪一年
     * @param colorOf    给一个日期，返回它的颜色；返回 null 表示"不在这年，画成透明占位"
     * @param tipSuffix  鼠标悬停时补在日期后面的说明，比如 "：已签到" / "：12 题"
     */
    public static List<List<Map<String, Object>>> rows(int year,
                                                       Function<LocalDate, String> colorOf,
                                                       Function<LocalDate, String> tipSuffix) {
        LocalDate firstMonday = LocalDate.of(year, 1, 1).with(DayOfWeek.MONDAY);
        LocalDate lastSunday = LocalDate.of(year, 12, 31).with(DayOfWeek.SUNDAY);

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        for (int r = 0; r < 7; r++) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (LocalDate d = firstMonday.plusDays(r); !d.isAfter(lastSunday); d = d.plusDays(7)) {
                Map<String, Object> cell = new LinkedHashMap<>();
                boolean inYear = d.getYear() == year;
                cell.put("date", d.toString());
                cell.put("inYear", inYear);
                if (inYear) {
                    String color = colorOf.apply(d);
                    cell.put("color", color == null ? "#dde3e7" : color);   // null = 没数据 = 明显的浅灰
                    String suffix = tipSuffix == null ? "" : tipSuffix.apply(d);
                    cell.put("tip", d.format(MD) + (suffix == null ? "" : suffix));
                } else {
                    // 不属于这一年的补位格：透明，但仍然占位（否则列会错位）
                    cell.put("color", "transparent");
                    cell.put("tip", "");
                }
                row.add(cell);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 生成 12 个月的表头：每个月占几列（用来让月份标签和格子对齐）。
     */
    public static List<Map<String, Object>> monthSpans(int year) {
        LocalDate firstMonday = LocalDate.of(year, 1, 1).with(DayOfWeek.MONDAY);
        LocalDate lastSunday = LocalDate.of(year, 12, 31).with(DayOfWeek.SUNDAY);

        // 先数每一列（每一周）属于哪个月（按这一列的周一算）→ 再合并相邻同月的列
        int[] monthOfWeek = new int[64];
        int weeks = 0;
        for (LocalDate d = firstMonday; !d.isAfter(lastSunday); d = d.plusDays(7)) {
            monthOfWeek[weeks++] = d.getMonthValue();
        }

        List<Map<String, Object>> spans = new ArrayList<>();
        int i = 0;
        while (i < weeks) {
            int m = monthOfWeek[i];
            int j = i;
            while (j < weeks && monthOfWeek[j] == m) {
                j++;
            }
            Map<String, Object> span = new LinkedHashMap<>();
            span.put("label", m + "月");
            span.put("span", j - i);
            spans.add(span);
            i = j;
        }
        return spans;
    }
}
