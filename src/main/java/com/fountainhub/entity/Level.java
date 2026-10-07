package com.fountainhub.entity;

/**
 * 学员所处阶段。
 *
 * 【为什么用枚举，而不是直接写 String】
 *   数据库那一列是 VARCHAR(20)，注释里写的合法值是 BEGINNER / INTERMEDIATE / CONTEST。
 *   如果代码里到处写字符串，把 "BEGINNER" 打成 "BEGINER"，编译器不会报错，
 *   要等某个统计页面数字不对了才发现。用枚举：打错字【编译就报错】。
 *   （和 Role、UserStatus 是同一个思路，见 Role.java 的注释。）
 *
 * 【为什么数据库里存字符串而不是数字】
 *   @Enumerated(EnumType.STRING) 会把枚举名原样存进去。
 *   这样直接打开数据库就能看懂，不用回来查代码（见 docs/05 第 1 节设计原则 7）。
 */
public enum Level {

    /** 入门 */
    BEGINNER("入门"),

    /** 进阶 */
    INTERMEDIATE("进阶"),

    /** 竞赛 */
    CONTEST("竞赛");

    /** 中文名，界面上显示用 */
    private final String label;

    Level(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
