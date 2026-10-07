package com.fountainhub.service;

import com.fountainhub.entity.SiteSetting;
import com.fountainhub.entity.SiteTextKeys;
import com.fountainhub.repository.SiteSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点配置的**唯一读写入口**（2026-10-07 新增；T-042 的首页背景图也走这里）。
 *
 * ===========================================================================
 * 【为什么要有这个 Service】
 *   在它之前，"读 site_settings"这件事是散着的：
 *     · HomeController 里直接 `settingRepository.findById(KEY_HOME_BACKGROUND)`
 *     · AdminSettingsController 里有个静态方法 readBackground(repo)
 *   再加 40 多个文案键，如果继续这样散着写，一定会出现"某个页面读、某个页面忘读"。
 *   → 统一到这里：**要配置就读它**。
 *
 * 【空值兜底（这是本功能的一条硬需求）】
 *   管理员没填的项，页面上**不能是空白**，要显示占位符（如「【请填写社团定位】」）。
 *   兜底放在 `allTexts()` 里做 —— 模板只管 `${siteText['key']}`，**永远拿到非空字符串** ✅
 *
 * 【⚠️ 关于性能】
 *   每请求会查一次 site_settings（一张小表，几十行）。社团规模下可以忽略。
 *   将来若真需要缓存，**只改这个类**即可（全站只有它在读这张表）。
 * ===========================================================================
 */
@Service
public class SiteSettingService {

    private final SiteSettingRepository settingRepository;

    public SiteSettingService(SiteSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    // ==================================================================================
    // 读
    // ==================================================================================

    /**
     * 全部配置的**原值**（空的还是空的）。
     * 给**后台编辑**用 —— 后台要如实显示"你到底填了没有"，不能拿占位符骗管理员。
     */
    public Map<String, String> rawValues() {
        Map<String, String> out = new LinkedHashMap<>();
        for (SiteSetting s : settingRepository.findAll()) {
            out.put(s.getSettingKey(), s.getSettingValue());
        }
        return out;
    }

    /**
     * 全部**文案**的页面显示值：登记过的键，空值一律换成占位符。
     * 给**前台模板**用 —— 模板里 `${siteText['home.hero.title']}` 保证非空 ✅
     */
    public Map<String, String> allTexts() {
        Map<String, String> raw = rawValues();
        Map<String, String> out = new LinkedHashMap<>();
        for (SiteTextKeys.Item it : SiteTextKeys.ALL) {
            out.put(it.getKey(), blankToPlaceholder(raw.get(it.getKey()), it.getPlaceholder()));
        }
        return out;
    }

    /** 单个文案的显示值（个别页面只想要一个值时用） */
    public String text(String key) {
        String v = rawValues().get(key);
        return blankToPlaceholder(v, SiteTextKeys.placeholderOf(key));
    }

    /**
     * 「一行一个」的文本 → 列表（价值观标签墙用）。
     * 配置为空时返回**占位符本身**，这样页面上至少能看到一条提示，而不是整块消失。
     */
    public List<String> lines(String key) {
        List<String> lines = SiteTextKeys.splitLines(rawValues().get(key));
        return lines.isEmpty() ? List.of(SiteTextKeys.placeholderOf(key)) : lines;
    }

    // ==================================================================================
    // 写
    // ==================================================================================

    /**
     * 批量保存文案。
     *
     * 🔒【安全点，必须守住】只接受 {@link SiteTextKeys} 里**登记过**的键。
     *   否则任何能进这个接口的人都能往 site_settings 里塞任意键
     *   （比如覆盖 home_background，或者将来别的敏感配置）。
     *
     * 【空值怎么存】存 **NULL**，不是空串 —— 这样"没填"和"填了空"是一回事，
     *   前台一律走占位符兜底（`allTexts()`）✅
     *
     * @param incoming 表单提交上来的 name→value（name 就是配置键）
     */
    @Transactional
    public void saveTexts(Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (SiteTextKeys.Item it : SiteTextKeys.ALL) {
            if (!incoming.containsKey(it.getKey())) {
                continue;                      // 表单里没有这一项 → 不动它（避免误清空）
            }
            String value = incoming.get(it.getKey());
            value = value == null ? null : value.trim();

            SiteSetting row = settingRepository.findById(it.getKey()).orElseGet(() -> {
                SiteSetting n = new SiteSetting();
                n.setSettingKey(it.getKey());
                return n;
            });
            row.setSettingValue(value == null || value.isEmpty() ? null : value);
            settingRepository.save(row);
        }
    }

    // ==================================================================================

    private static String blankToPlaceholder(String value, String placeholder) {
        return (value == null || value.isBlank()) ? placeholder : value;
    }
}
