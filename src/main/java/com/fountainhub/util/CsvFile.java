package com.fountainhub.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV 下载工具（T-036 新建）—— 把「往外写 CSV」这件事**收口到一处**。
 *
 * ===========================================================================
 * 【为什么要有这个类】
 *   这段逻辑原来是写在 `AttendanceExportController` 里的（T-022）。
 *   T-036 又要导出「学员名单」和「获奖清单」—— **再抄第三遍就该收口了**，
 *   和 R-006 抽 `FormUtils`、T-033 抽 `UserDisplay` 是同一个理由：
 *   一份逻辑写三遍，将来改一处（比如换 POI 导真 .xlsx）就得改三个地方，必漏。
 *
 * 【它负责的三件事（都是踩过坑才写对的）】
 *   ① **UTF-8 BOM**：不加的话 Excel 打开是乱码（中文全变问号）—— CSV 的经典坑；
 *   ② **中文文件名**：必须按 RFC 5987 用 `filename*=UTF-8''<百分号编码>`，
 *      否则浏览器把文件名存成一堆乱码；同时留一个纯 ASCII 的 `filename=` 兜底老浏览器；
 *   ③ **RFC 4180 转义**：单元格里含逗号 / 引号 / 换行时要整体加引号、内部引号翻倍。
 *
 * 【为什么返回 ResponseEntity<byte[]>】
 *   控制器直接 `return CsvFile.download(...)` 即可，调用方不用关心这些细节。
 *
 * ⚠️ **为什么不导 .xlsx**：项目负责人想要真 Excel，但**开发沙箱没有外网**、
 *   本地 Maven 仓库里也没有 Apache POI（实测 `org/apache/poi` 目录不存在），
 *   依赖加不上。所以统一先导 **CSV（UTF-8 BOM，Excel / WPS 双击可正常打开）**。
 *   将来 POI 到位后：**只改本类**（把 toCsv 换成 POI 写法、后缀与 Content-Type 改掉），
 *   三个导出的控制器**一行都不用动**。
 * ===========================================================================
 */
public final class CsvFile {

    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 工具类不给 new —— 全是静态方法 */
    private CsvFile() {
    }

    /**
     * 把一张表转成「可下载的 CSV 响应」。
     *
     * @param chineseFileNamePrefix 中文文件名前缀（浏览器实际存下来的名字），如「学员名单」
     * @param asciiFileNamePrefix   纯 ASCII 前缀（给老浏览器兜底），如「students」
     * @param table                 第 0 行是表头，之后每行一条记录；null 会被当成空字符串
     */
    public static ResponseEntity<byte[]> download(String chineseFileNamePrefix,
                                                 String asciiFileNamePrefix,
                                                 List<List<String>> table) {
        byte[] body = ("\uFEFF" + toCsv(table)).getBytes(StandardCharsets.UTF_8);

        String date = LocalDate.now().format(FILE_DAY);
        String filename = chineseFileNamePrefix + "-" + date + ".csv";
        String asciiFallback = asciiFileNamePrefix + "-" + date + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /** 把二维表拼成 CSV 文本（不含 BOM；BOM 在 download 里加） */
    public static String toCsv(List<List<String>> table) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : table) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row.get(i)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /** CSV 转义：内容里如果有逗号/引号/换行，要按 RFC4180 包起来 */
    public static String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** 小便利：null 与可能为 null 的对象，统一转成「空字符串」而不是 "null" */
    public static String text(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
