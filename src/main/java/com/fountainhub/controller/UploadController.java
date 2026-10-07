package com.fountainhub.controller;

import com.fountainhub.entity.User;
import com.fountainhub.security.CurrentUserService;
import com.fountainhub.service.FileStorageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片上传接口（T-025）。
 *
 * 【为什么返回 JSON 而不是跳页面】
 *   前端用 fetch 上传，拿到 URL 后：
 *     · 封面图 → 填到隐藏字段 + 显示预览
 *     · 正文插图 → 直接插进 Markdown 文本框
 *   所以这里返 { "url": "/uploads/2026/10/xxx.png" } 最顺手。
 *
 * 🔒 【权限】必须登录，且是社长层（和新闻管理一致）——
 *   不能让人随便往服务器上写文件。
 * ===========================================================================
 */
@RestController
public class UploadController {

    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    public UploadController(FileStorageService fileStorageService, CurrentUserService currentUserService) {
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/admin/upload")
    @PreAuthorize("hasAnyRole('TEACHER','PRESIDENT','VICE_PRESIDENT')")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file, Authentication authentication) {
        User me = currentUserService.require(authentication);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String url = fileStorageService.store(file, me.getId());
            result.put("ok", true);
            result.put("url", url);
        } catch (IllegalArgumentException e) {
            // 后端的异常信息本来就是给人看的（"只允许上传图片…"），直接回传
            result.put("ok", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
