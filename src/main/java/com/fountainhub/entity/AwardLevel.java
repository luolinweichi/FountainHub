package com.fountainhub.entity;

/**
 * 获奖「级别」（T-032）—— 对应 `awards.award_level` 这一列。
 *
 * ⚠️ 枚举名必须和数据库注释一字不差（SCHOOL / CITY / PROVINCE / NATIONAL / INTERNATIONAL）——
 *   写错的话，`ddl-auto: validate` 虽然查不出来（这一列是 VARCHAR），
 *   但页面上的中文会和库里存的英文对不上，属于"静默不一致"。
 *   所以**改这里之前先看 sql/01-schema.sql 第 234 行的注释**。
 */
public enum AwardLevel {

    SCHOOL("校级"),
    CITY("市级"),
    PROVINCE("省级"),
    NATIONAL("国家级"),
    INTERNATIONAL("国际级");

    private final String label;

    AwardLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
