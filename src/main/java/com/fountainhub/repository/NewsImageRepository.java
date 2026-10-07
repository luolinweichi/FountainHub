package com.fountainhub.repository;

import com.fountainhub.entity.NewsImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 新闻配图仓库（T-023）。
 */
public interface NewsImageRepository extends JpaRepository<NewsImage, Long> {

    /** 取某条新闻的全部配图，按排序值升序（T-025 / T-026 用） */
    List<NewsImage> findByNewsIdOrderBySortOrderAsc(Long newsId);
}
