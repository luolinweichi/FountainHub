package com.fountainhub.service;

import com.fountainhub.entity.Award;
import com.fountainhub.entity.AwardStudent;
import com.fountainhub.entity.User;
import com.fountainhub.repository.AwardRepository;
import com.fountainhub.repository.AwardStudentRepository;
import com.fountainhub.repository.UserRepository;
import com.fountainhub.util.UserDisplay;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 获奖「对外公开」口径的**唯一来源**（T-033）。
 *
 * ===========================================================================
 * 🔒🔒 【这个类就是红线的实现】
 *
 *   红线原话：**「获奖对外展示必须经该学员本人同意」**（`award_students.is_public`）。
 *
 *   所以这里做两件**必须一起做**的事：
 *     ① **只把 `is_public = 1` 的成员放进结果**（不是"页面不显示"，而是**模型里就没有**）——
 *        这样未同意者的姓名**根本不会出现在 HTML 里**，F12 也看不到；
 *     ② **一条奖如果没有任何人同意公开，整条丢弃** ——
 *        否则"某人得了某奖"这件事本身就泄露了（他没同意公开这条获奖）。
 *
 * 【为什么要有这个 Service，而不是在两个 Controller 里各写一遍】
 *   首页（`HomeController`）和展示墙（`PublicAwardController`）都要这份数据。
 *   T-041 的教训：**公开页的红线只有一条，不能这里松、那里紧** ——
 *   所以规则只写在这里，两边都调它。
 *
 * ⚠️ 返回值里**只有**：获奖本体 + 同意公开者的**姓名**。
 *    绝不含手机号 / 学号 / 班级 / QQ / 微信 / 邮箱，也**不含证书图片路径**
 *    （团队奖证书上印着全部成员姓名，其中可能有人不同意公开 —— 见 T-033 任务书）。
 * ===========================================================================
 */
@Service
public class PublicAwardService {

    private final AwardRepository awardRepository;
    private final AwardStudentRepository awardStudentRepository;
    private final UserRepository userRepository;

    public PublicAwardService(AwardRepository awardRepository,
                              AwardStudentRepository awardStudentRepository,
                              UserRepository userRepository) {
        this.awardRepository = awardRepository;
        this.awardStudentRepository = awardStudentRepository;
        this.userRepository = userRepository;
    }

    /**
     * 所有**可以对外展示**的获奖（已按日期倒序，与后台列表同序）。
     *
     * @return 每项形如 `{ award: Award, memberNames: List<String>, team: boolean, year: Integer }`
     */
    public List<Map<String, Object>> publicRows() {
        // 【为什么一次全查】获奖是几十条的量级；每条去查一次关联/用户就是 N+1。
        Map<Long, List<AwardStudent>> linksByAward = new HashMap<>();
        for (AwardStudent as : awardStudentRepository.findAll()) {
            linksByAward.computeIfAbsent(as.getAwardId(), k -> new ArrayList<>()).add(as);
        }
        Map<Long, User> userById = new HashMap<>();
        for (User u : userRepository.findAll()) {
            userById.put(u.getId(), u);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Award a : awardRepository.findAllByOrderByAwardDateDescIdDesc()) {
            List<String> names = new ArrayList<>();
            for (AwardStudent as : linksByAward.getOrDefault(a.getId(), Collections.emptyList())) {
                if (Boolean.TRUE.equals(as.getIsPublic())) {      // 🔒 ① 只要本人同意的
                    names.add(UserDisplay.name(userById.get(as.getUserId())));
                }
            }
            if (names.isEmpty()) {
                continue;                                        // 🔒 ② 全票不同意 → 整条不出现
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("award", a);
            row.put("memberNames", names);
            row.put("team", names.size() > 1);                   // 多人 = 团队奖（页面上标一下）
            row.put("year", a.getAwardDate() == null ? null : a.getAwardDate().getYear());
            rows.add(row);
        }
        return rows;
    }
}
