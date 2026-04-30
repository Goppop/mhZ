package com.policyradar.sources.html;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单个列表项或详情页的提取结果。
 *
 * fields 中 url 已是绝对地址，publishDate 仍是字符串原文（解析结果在 details 的 diagnostic 中）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedItem {

    /** fieldName → value（url 必为绝对地址） */
    private Map<String, String> fields;

    /** fieldName → 完整求值结果（含诊断），供预览路径使用 */
    private Map<String, RuleEvalResult> details;

    /** 列表项文本摘要（前 200 字符），详情页提取时可为 null */
    private String itemText;
}