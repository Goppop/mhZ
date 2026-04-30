package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;

import java.util.List;

/**
 * HTML 抽取引擎。
 *
 * 统一编排列表页和详情页的字段提取流程。
 * 正式抓取（HtmlRunner）和后续预览服务均调用此接口。
 */
public interface HtmlExtractionEngine {

    /**
     * 从 HTML 中按 itemSelector 切割列表项，对每项应用提取规则。
     *
     * @param html         页面 HTML
     * @param itemSelector 列表项 CSS 选择器
     * @param rules        列表字段提取规则
     * @param baseUrl      用于相对 URL 补全
     * @return 提取结果列表（含字段值和诊断）
     */
    List<ExtractedItem> extractList(String html, String itemSelector,
                                     List<PolicyExtractRule> rules, String baseUrl);

    /**
     * 从详情页 HTML 中提取字段。
     *
     * @param html    详情页 HTML
     * @param rules   详情页提取规则
     * @param baseUrl 用于相对 URL 补全
     * @return 提取结果（含字段值和诊断）
     */
    ExtractedItem extractDetail(String html, List<PolicyExtractRule> rules, String baseUrl);
}