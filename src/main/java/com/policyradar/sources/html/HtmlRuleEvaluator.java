package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;
import org.jsoup.nodes.Element;

import java.util.List;

/**
 * HTML 字段规则求值器。
 *
 * 负责单条规则的取值执行、同字段多规则兜底、以及全规则诊断。
 * 预览和正式抓取共用同一实现，区别仅在于调用 evaluateFirst 还是 evaluateAll。
 */
public interface HtmlRuleEvaluator {

    /**
     * 对指定元素执行单条提取规则。
     *
     * @param root 当前作用域根元素（列表 item 或详情页 document）
     * @param rule 提取规则
     * @return 求值结果，包含提取值、是否命中、诊断信息
     */
    RuleEvalResult evaluate(Element root, PolicyExtractRule rule);

    /**
     * 对同一字段的所有兜底规则依次尝试，返回第一条命中的结果。
     * 所有规则都不命中时返回空结果（含失败诊断）。
     *
     * @param root      当前作用域根元素
     * @param rules     该字段的全部规则（按 sortOrder 排序）
     * @param fieldName 字段名
     * @return 求值结果（可能为未命中状态）
     */
    RuleEvalResult evaluateFirst(Element root, List<PolicyExtractRule> rules, String fieldName);

    /**
     * 对同一字段所有规则执行求值，返回全部结果（含命中和未命中）。
     * 用于预览时展示每条规则的诊断。
     *
     * 仅供预览路径调用，正式抓取必须使用 {@link #evaluateFirst}。
     * 否则正式抓取会做无谓的全规则计算。
     *
     * @param root      当前作用域根元素
     * @param rules     该字段的全部规则
     * @param fieldName 字段名
     * @return 所有规则的求值结果列表
     */
    List<RuleEvalResult> evaluateAll(Element root, List<PolicyExtractRule> rules, String fieldName);
}