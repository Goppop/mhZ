package com.policyradar.sources.html;

import lombok.Builder;
import lombok.Data;

/**
 * 字段规则草稿 —— 前端传过来的单条字段提取规则。
 * 对应前端 types/htmlConfig.ts 中的 FieldRuleDraft。
 */
@Data
@Builder
public class FieldRuleDraft {
    /** 字段名（title / url / publishDate 等） */
    private String fieldName;
    /** CSS 选择器，空串 = 取 item 自身 */
    private String selector;
    /** 取值方式：TEXT / ATTR / HTML / CONST / REGEX */
    private String valueType;
    /** ATTR 类型时必填的属性名（如 href、title） */
    private String attrName;
    /** CONST 类型时的固定值 */
    private String constValue;
    /** REGEX 类型时的正则，也可作为后处理 */
    private String regexPattern;
    /** 日期格式（仅 publishDate 字段） */
    private String dateFormat;
    /** 是否为必填字段 */
    @Builder.Default
    private boolean required = false;
    /** 同字段多规则时的尝试顺序 */
    @Builder.Default
    private int sortOrder = 0;
}
