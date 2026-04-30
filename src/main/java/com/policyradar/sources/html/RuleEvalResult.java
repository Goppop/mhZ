package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条规则求值结果。
 *
 * 包含提取值、是否命中、生效的规则和诊断详情。
 * 预览和正式抓取共用此模型，预览路径额外使用 diagnostic 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEvalResult {

    /** 字段名 */
    private String fieldName;

    /** 提取到的值（null 表示未命中） */
    private String value;

    /** 是否提取成功 */
    private boolean matched;

    /** 生效的规则（未命中时为尝试的规则） */
    private PolicyExtractRule rule;

    /** 诊断详情 */
    private RuleDiagnostic diagnostic;
}