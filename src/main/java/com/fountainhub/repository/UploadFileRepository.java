package com.fountainhub.repository;

import com.fountainhub.entity.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 上传文件仓库（T-025）。
 */
public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {
}
