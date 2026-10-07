package com.fountainhub.repository;

import com.fountainhub.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 站点配置仓库（T-042）。
 * 主键是 String（配置项名），所以是 JpaRepository<SiteSetting, String>。
 */
public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
}
