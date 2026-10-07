package com.fountainhub.util;

/**
 * 表单处理的小工具（R-006）。
 *
 * 【为什么要有它】
 *   同一段小逻辑原先被抄了好几遍，改一处就要改好几处：
 *     blankToNull()  —— 抄了 3 遍（AdminStudentController / ProfileController / RegisterController）
 *     parseLevel()   —— 抄了 2 遍（AdminStudentController / RegisterController）
 *     parseRole() / parseStatus() —— 同一模式又写了一遍
 *   现在全部收进这里，**只有一份**。
 *
 * 【⚠️ 行为绝对不能改】
 *   `parseEnum` 遇到非法值【返回 null】，**不是抛异常**。
 *   如果"顺手"改成抛异常，页面会从"友好提示"变成 **500**。
 */
public final class FormUtils {

    /** 工具类不允许被 new（全是静态方法） */
    private FormUtils() {
    }

    /**
     * 把「空字符串」和「全是空格」统一变成 `null`。
     *
     * 【为什么需要】
     *   用户不填的输入框，浏览器提交上来的是空字符串 `"`，不是 null。
     *   存进数据库时，我们更希望它是一句 @@"这栏没填"@@"的 `null`。
     */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 去掉首尾空格；`null` 变成空字符串 `"`。
     *
     * 【和 blankToNull 的区别】这个**保证返回非 null**，所以可以安全地接着调 `.isEmpty()`。
     * 注册页那几个"必填"字段用的就是它 —— 先 trim 成空串，再统一判断是否为空。
     */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 把表单里的字符串转成 Long（T-016 加的）。
     *
     * 【为什么要自己转，不直接写 `@RequestParam Long groupId`】
     *   因为下拉框"不选"时提交上来的是**空字符串** `"`。
     *   直接让 Spring 转 Long 会失败 → 抛 400 错误页，用户看到一个看不懂的错误。
     *   自己转的话：空串和非法值都当 `null`（= "未分组"），容错更好。
     */
    public static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 把表单里的字符串转成枚举，**非法值一律返回 null**。
     *
     * 【为什么不能抛异常】见类注释 —— 那会让页面从友好提示变成 500。
     *
     * @param type 目标枚举类型，例如 `Role.class`
     * @param raw  用户提交的原始字符串，例如 `"teacher"`
     */
    /**
     * 把表单里的日期字符串转成 LocalDate（T-020）。转不了就返回 null ——
     * 让调用方给一句人话提示，而不是抛 500。
     *
     * 【为什么要 try/catch】用户可能在地址栏/表单里塞任何东西进来。
     */
    public static java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    public static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;   // 非法值 → null（调用方负责给友好提示）
        }
    }
}
