package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML 抽取引擎默认实现。
 *
 * 组合 HtmlRuleEvaluator，编排列表页和详情页的字段提取流程。
 * 负责 URL 补全，不持有提取逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultExtractionEngine implements HtmlExtractionEngine {

    private final HtmlRuleEvaluator ruleEvaluator;

    @Override
    public List<ExtractedItem> extractList(String html, String itemSelector,
                                            List<PolicyExtractRule> rules, String baseUrl) {
        if (html == null || html.isBlank()) {
            return new ArrayList<>();
        }
        if (itemSelector == null || itemSelector.isBlank()) {
            return new ArrayList<>();
        }
        if (rules == null || rules.isEmpty()) {
            return new ArrayList<>();
        }

        List<ExtractedItem> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(itemSelector.trim());

        for (int i = 0; i < items.size(); i++) {
            Element itemElement = items.get(i);
            ExtractedItem extracted = extractFields(itemElement, rules, baseUrl);
            if (extracted != null) {
                String text = itemElement.text();
                if (text != null && text.length() > 200) {
                    extracted.setItemText(text.substring(0, 200));
                } else {
                    extracted.setItemText(text);
                }
                results.add(extracted);
            }
        }

        return results;
    }

    @Override
    public ExtractedItem extractDetail(String html, List<PolicyExtractRule> rules, String baseUrl) {
        if (html == null || html.isBlank()) {
            return ExtractedItem.builder()
                    .fields(new LinkedHashMap<>())
                    .details(new LinkedHashMap<>())
                    .build();
        }
        if (rules == null || rules.isEmpty()) {
            return ExtractedItem.builder()
                    .fields(new LinkedHashMap<>())
                    .details(new LinkedHashMap<>())
                    .build();
        }

        Document doc = Jsoup.parse(html);
        return extractFields(doc, rules, baseUrl);
    }

    /**
     * 对单个根元素应用全部字段提取规则。
     *
     * @param root    作用域根元素（列表 item 或详情页 document）
     * @param rules   提取规则列表
     * @param baseUrl 用于相对 URL 补全
     * @return 提取结果，所有字段都不命中时返回 null
     */
    private ExtractedItem extractFields(Element root, List<PolicyExtractRule> rules, String baseUrl) {
        Map<String, List<PolicyExtractRule>> rulesByField = groupRulesByField(rules);
        if (rulesByField.isEmpty()) {
            return null;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, RuleEvalResult> details = new LinkedHashMap<>();
        boolean hasAnyValue = false;

        for (Map.Entry<String, List<PolicyExtractRule>> entry : rulesByField.entrySet()) {
            String fieldName = entry.getKey();
            List<PolicyExtractRule> fieldRules = entry.getValue();

            // 正式抓取路径使用 evaluateFirst
            RuleEvalResult result = ruleEvaluator.evaluateFirst(root, fieldRules, fieldName);
            details.put(fieldName, result);

            if (result.isMatched()) {
                String value = result.getValue();
                // URL 字段：相对路径补全
                if ("url".equals(fieldName) && value != null && !value.isEmpty()) {
                    String resolved = resolveUrl(baseUrl, value);
                    if (resolved != null && !resolved.equals(value)) {
                        result.getDiagnostic().setUrlResolved(true);
                    }
                    value = resolved;
                }
                fields.put(fieldName, value);
                hasAnyValue = true;
            }
        }

        if (!hasAnyValue) {
            return null;
        }

        return ExtractedItem.builder()
                .fields(fields)
                .details(details)
                .build();
    }

    /**
     * 按字段名分组规则，保持插入顺序。
     */
    private Map<String, List<PolicyExtractRule>> groupRulesByField(List<PolicyExtractRule> rules) {
        Map<String, List<PolicyExtractRule>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            PolicyExtractRule rule = rules.get(i);
            if (rule == null) {
                continue;
            }
            String fieldName = rule.getFieldName();
            if (fieldName == null) {
                continue;
            }

            List<PolicyExtractRule> fieldRules = grouped.get(fieldName);
            if (fieldRules == null) {
                fieldRules = new ArrayList<>();
                grouped.put(fieldName, fieldRules);
            }
            fieldRules.add(rule);
        }
        return grouped;
    }

    /**
     * 解析相对 URL 为绝对 URL。
     */
    private String resolveUrl(String baseUrl, String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            return URI.create(baseUrl).resolve(url.trim()).toString();
        } catch (Exception e) {
            log.debug("URL 补全失败: baseUrl={}, url={}", baseUrl, url);
            return url;
        }
    }
}