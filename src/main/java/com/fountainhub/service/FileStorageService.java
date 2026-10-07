package com.fountainhub.service;

import com.fountainhub.entity.UploadFile;
import com.fountainhub.repository.UploadFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片存储（T-025）。
 *
 * ===========================================================================
 * 🔒 这个类是全项目【唯一】把"用户传来的东西"写到磁盘上的地方，所以规矩最严：
 *
 *   ① **后缀白名单**：只有 jpg/jpeg/png/gif/webp 能进来。
 *      否则有人传个 .jsp / .html / .exe，将来万一被当静态资源执行就是大事。
 *
 *   ② **落盘的文件名一律是随机 UUID**，格式 yyyy/MM/<uuid>.<后缀>。
 *      ⚠️ **绝不使用用户传的文件名**：
 *        · 用户传 ../../etc/passwd → 会写到项目目录外面（**路径穿越**）
 *        · 用户传 a.png 覆盖别人传的 a.png → 把别人的图顶掉
 *        随机 UUID 两个问题一起解决。原始文件名只存数据库、只给人看。
 *
 *   ③ **按年月分子目录**（2026/10/xxx.png）——
 *      将来图多了，一个目录塞几万张会很难管。
 *
 *   ④ 大小上限交给 Spring 的 multipart 配置（application.yml，5MB），
 *      这里不再重复判断 —— 一处规则比两处好维护。
 * ===========================================================================
 */
@Service
public class FileStorageService {

    /** 🔒 白名单：只允许这几种图片 */
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 浏览器访问图片时用的前缀（和 application.yml 里的静态映射对应） */
    public static final String URL_PREFIX = "/uploads/";

    private final UploadFileRepository uploadFileRepository;

    @Value("${fountainhub.upload.dir:./uploads/}")
    private String uploadDir;

    public FileStorageService(UploadFileRepository uploadFileRepository) {
        this.uploadFileRepository = uploadFileRepository;
    }

    /**
     * 保存一张图，返回可以直接放进 <img src> 的 URL。
     *
     * @throws IllegalArgumentException 后缀不在白名单 / 文件为空 / 写盘失败
     */
    public String store(MultipartFile file, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("没有选择文件。");
        }

        // ① 取后缀并检查白名单
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extOf(original);

        // 🔄【2026-10-07 修的真 bug】裁剪后的图是 canvas.toBlob() 出来的 **Blob**，
        //   浏览器给它的 filename 是 "blob" —— **完全没有后缀** → 原来直接被白名单拒掉，
        //   用户看到「只允许上传图片（jpg / jpeg / png / gif / webp）」，
        //   可他传的明明就是一张 png ❌（新闻封面图那条链路就是这么挂的）
        //   ✅ 兜底：后缀拿不到（或不在白名单）时，再看浏览器给的 Content-Type。
        //   ⚠️ Content-Type 是客户端说的，不能全信 —— 但它【只用来决定后缀】，
        //      而后缀仍然必须落在白名单里、落盘名仍然是随机 UUID → 不引入新风险 ✅
        if (!ALLOWED.contains(ext)) {
            ext = extFromContentType(file.getContentType());
        }
        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException("只允许上传图片（jpg / jpeg / png / gif / webp）。");
        }

        // ② 随机文件名 + 按年月分目录
        String rel = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = base.resolve(rel).normalize();
            // 再确认一次：解析后的路径必须在 base 里面（双保险，防路径穿越）
            if (!target.startsWith(base)) {
                throw new IllegalArgumentException("非法的文件路径。");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("保存图片失败：" + e.getMessage());
        }

        // ③ 登记到数据库（原始名只存这里，不参与落盘）
        UploadFile uf = new UploadFile();
        // 原始名只存数据库、给人看。⚠️ 裁剪出来的 Blob 原始名是 "blob"，没有后缀很难看，
        //   这里补上后缀（纯展示用，**不参与落盘**）
        String displayName = original;
        if (extOf(displayName).isEmpty()) {
            displayName = (displayName.isEmpty() ? "upload" : displayName) + "." + ext;
        }
        uf.setOriginalName(displayName.length() > 255 ? displayName.substring(0, 255) : displayName);
        uf.setStoredPath(rel);
        uf.setContentType(file.getContentType());
        uf.setSizeBytes(file.getSize());
        uf.setUploaderId(uploaderId);
        uploadFileRepository.save(uf);

        return URL_PREFIX + rel;
    }

    /** 从文件名取小写后缀；取不到（或最后一个字符就是点）返回空串 */
    private static String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 从 Content-Type 反推后缀（只在文件名没有后缀时兜底用）。
     * 只认白名单里的那几种图片类型，其它一律返回空串（= 仍然会被拒绝）。
     */
    private static String extFromContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String ct = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');                 // 有些浏览器会给 "image/png;charset=utf-8"
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        switch (ct) {
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            default:
                return "";
        }
    }
}
