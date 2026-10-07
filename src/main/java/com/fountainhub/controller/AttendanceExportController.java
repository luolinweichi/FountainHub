package com.fountainhub.controller;

import com.fountainhub.entity.AttendanceEvent;
import com.fountainhub.entity.AttendanceRecord;
import com.fountainhub.entity.AttendanceStatus;
import com.fountainhub.entity.AttendanceType;
import com.fountainhub.entity.Role;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AttendanceEventRepository;
import com.fountainhub.repository.AttendanceRecordRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.CsvFile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 考勤台账导出（T-022 / FR-6.12）—— ⭐ **两个独立接口，点哪个导哪个**。
 *
 * ===========================================================================
 * 【两个接口（2026-10-03 项目负责人定）】
 *   /admin/attendance/export/weekly  → **仅例行活动**（"必到那天谁没来"）
 *   /admin/attendance/export/all     → **全部日期**（含平时的日常开放）
 *
 * 【表格格式（项目负责人定的）】
 *          │ 9月30日 │ 10月1日 │ 10月2日 │ ……
 *   ───────┼─────────┼─────────┼─────────┼────
 *   普通社员 │   ✓     │   ✓     │         │
 *   社长     │   ✓     │         │   ✓     │
 *   行 = 学生；第一列 = 姓名；后面 = 日期；**到了打 ✓，没到就空着**（不写"缺席"两个字）。
 *
 * 【权限】指导老师 / 正社长 / 副社长。**组长 403**（和 T-020 一致）。
 * 🔒 **不含手机号**（FR-11.3 的硬性要求）。
 *
 * ===========================================================================
 * 🔄 【2026-10-04 T-036 改动：往外写的那段搬进了 util/CsvFile】
 *   原来 BOM / RFC5987 文件名 / CSV 转义这三件事都写在本类里。T-036 又要导出
 *   「学员名单」「获奖清单」，**再抄第三遍就该收口**（同 R-006 / UserDisplay 的理由），
 *   所以抽成了 {@link CsvFile}。**本类的表格算法（buildTable）一行没动**，
 *   导出的**内容逐字节一致**，只是两个接口的 ASCII 兜底文件名不再相同
 *   （原来是 `attendance-<日期>.csv`，两个接口一样，同事下载两次会互相覆盖）。
 *
 * ⚠️ 【为什么是 CSV 不是真 .xlsx】沙箱没有外网、本地 Maven 仓库里没有 POI，
 *   依赖加不上。等 POI 到位后：**只改 `CsvFile` 一个类**，本类无需改动。
 * ===========================================================================
 */
@Controller
@PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
public class AttendanceExportController {

    private final AttendanceEventRepository eventRepository;
    private final AttendanceRecordRepository recordRepository;
    private final UserRepository userRepository;

    public AttendanceExportController(AttendanceEventRepository eventRepository,
                                      AttendanceRecordRepository recordRepository,
                                      UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
    }

    /** ① 仅例行活动 */
    @GetMapping("/admin/attendance/export/weekly")
    public ResponseEntity<byte[]> exportWeekly() {
        return CsvFile.download("考勤表-仅例行活动", "attendance-weekly", buildTable(true));
    }

    /** ② 全部日期 */
    @GetMapping("/admin/attendance/export/all")
    public ResponseEntity<byte[]> exportAll() {
        return CsvFile.download("考勤表-全部日期", "attendance-all", buildTable(false));
    }

    // ==================================================================
    // 表格内容（⭐ 换文件格式时这一块完全不用动）
    // ==================================================================

    /**
     * 把"要导出的表"算出来：第 0 行是表头，之后每行是一个学员。
     *
     * @param weeklyOnly true = 只取例行活动；false = 取全部活动
     */
    private List<List<String>> buildTable(boolean weeklyOnly) {
        // ① 选出要出现在表头里的活动（按日期从左到右）
        List<AttendanceEvent> events = new ArrayList<>();
        for (AttendanceEvent e : eventRepository.findAllByOrderByEventDateDesc()) {
            if (weeklyOnly && e.getType() != AttendanceType.WEEKLY_MEETING) {
                continue;
            }
            events.add(e);
        }
        events.sort(Comparator.comparing(AttendanceEvent::getEventDate));

        // ② "谁在哪一场到了" —— 一次查完，不在循环里查库
        Set<String> present = new HashSet<>();
        for (AttendanceRecord r : recordRepository.findAll()) {
            if (r.getStatus() == AttendanceStatus.PRESENT) {
                present.add(r.getEventId() + ":" + r.getUserId());
            }
        }

        // ③ 组表
        List<List<String>> table = new ArrayList<>();
        List<String> header = new ArrayList<>();
        header.add("学生姓名");
        for (AttendanceEvent e : events) {
            header.add(e.getEventDate().getMonthValue() + "月" + e.getEventDate().getDayOfMonth() + "日");
        }
        table.add(header);

        for (User u : userRepository.findAllByOrderByRealNameAsc()) {
            if (u.getRole() != Role.STUDENT) {
                continue;
            }
            List<String> row = new ArrayList<>();
            row.add(u.getRealName());                       // 🔒 只有姓名，没有手机号
            for (AttendanceEvent e : events) {
                // 到了打勾；没到就留空（不写"缺席"）
                row.add(present.contains(e.getId() + ":" + u.getId()) ? "✓" : "");
            }
            table.add(row);
        }
        return table;
    }
}
