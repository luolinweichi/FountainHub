package com.fountainhub.controller;

import com.fountainhub.entity.StudyGroup;
import com.fountainhub.entity.User;
import com.fountainhub.repository.StudyGroupRepository;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.service.AttendanceCalendarService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 「我的主页」（T-021 建立 → **2026-10-04 改版**）。
 *
 * ===========================================================================
 * 🔄 【2026-10-04 改了什么，为什么改】（项目负责人决定，开源脱敏同期）
 *
 *   ① **撤掉「学习热力图」（七级绿，数据来自 checkins）**
 *      理由（项目负责人的原话意思）：**其他社团不需要这么多东西** ——
 *      热力图的数据来自"内置题库"，而题库是二期功能、一期根本没有数据；
 *      对外开源的通用社团管理系统留着它只会让人困惑。
 *      ⚠️ 删得很彻底（2026-10-04 当天又追加了一步，请以此为准）：
 *        · 界面：本页的热力图卡片 → 换成签到日历；
 *        · 代码：`Checkin` / `CheckinMetric` / `CheckinSource` 实体 + `CheckinRepository` 仓库**已删除**；
 *        · 数据表：**`checkins` 表也已删除**（已建库的人跑 `sql/05-drop-checkins.sql`）；
 *        · 后台总览的「本周做题」KPI 与「做题排行榜」一并删除。
 *        · 🔒 但 `CheckinType` 枚举**保留** —— 它是**考勤**在用的
 *          （`attendance_records.checkin_type` = 本人签到 / 社长代打卡），名字像但完全无关，别顺手删。
 *        · 这些代码与表结构都在 **git 历史里完整保留**，将来若要恢复可以捞回来。
 *
 *   ② **把「考勤」（签到日历）放到原来热力图的位置，成为本页主体**
 *      数据和画法与 `/me/attendance` **完全一致** —— 都调
 *      {@link AttendanceCalendarService#fillCalendarCard}，
 *      **规则只有一份**，不会出现"两个页面算得不一样"。
 *      （两张图的区别只是颜色：签到日历两色蓝/灰；原来的热力图是七级绿。）
 *
 *   ③ **删掉 `/study`（学习窗口占位页）路由** —— 导航栏的「学习」入口也一并撤掉了，
 *      因为"其他社团也不需要学习"。卡片/入口都没了，留着路由就是死链。
 *
 * 【本页现在有什么】
 *   ① 头像区（姓名 / 学号 / 组名）
 *   ② ⭐ 签到日历（今日/本周/累计签到 + 日历格子 + 连续周数 + 最近一次 + 鼓励）
 *   ③ 常用功能入口：我的考勤 · 我的资料 · 我的获奖 · 修改密码
 *
 * 🔒 【公开性】本页要登录才能进；即便如此，**只放姓名/学号/组名，绝不放手机号**
 *    （与全站"手机号不对外展示"的红线一致）。
 * ===========================================================================
 */
@Controller
@RequestMapping("/me")
public class MyHomeController {

    private final StudyGroupRepository groupRepository;
    private final CurrentUserService currentUserService;
    private final AttendanceCalendarService attendanceCalendarService;

    public MyHomeController(StudyGroupRepository groupRepository,
                            CurrentUserService currentUserService,
                            AttendanceCalendarService attendanceCalendarService) {
        this.groupRepository = groupRepository;
        this.currentUserService = currentUserService;
        this.attendanceCalendarService = attendanceCalendarService;
    }

    /**
     * 我的主页。
     *
     * @param year 年份左右箭头带的参数（null = 今年）；两个页面用的是同一个参数名 `year`
     */
    @GetMapping
    public String home(@org.springframework.web.bind.annotation.RequestParam(required = false) Integer year,
                       Authentication authentication, Model model) {
        User me = currentUserService.require(authentication);

        // ---------- ① 签到日历（与 /me/attendance 同一张图、同一份规则）----------
        // 🔒 userId 从会话取（me.getId()），**不是**从 URL 参数取。
        attendanceCalendarService.fillCalendarCard(model, me.getId(), year);

        // ---------- ② 头像区需要的资料 ----------
        model.addAttribute("meName", me.getRealName());
        model.addAttribute("meStudentNo", me.getStudentNo());
        if (me.getGroupId() != null) {
            StudyGroup g = groupRepository.findById(me.getGroupId()).orElse(null);
            model.addAttribute("meGroupName", g == null ? null : g.getName());
        } else {
            model.addAttribute("meGroupName", null);
        }

        return "me/index";
    }
}
