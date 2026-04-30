package com.policyradar.sources.html;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 规则求值诊断信息。
 *
 * 承载单条规则执行后的详细诊断，供预览展示和调试使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDiagnostic {

    /** CSS 选择器命中元素数 */
    private int elementsMatchedBySelector;

    /** 提取文本长度（TEXT 模式），null 表示不适用 */
    private Integer extractedTextLength;

    /** 日期解析是否成功（仅 publishDate 字段） */
    private boolean dateParseSuccess;

    /** 日期解析结果（仅 publishDate 字段，value 仍是字符串原文） */
    private LocalDate parsedDate;

    /** URL 是否已补全为绝对地址（仅 url 字段） */
    private boolean urlResolved;

    /** 失败原因（可展示给用户），null 表示成功 */
    private String failureReason;
}