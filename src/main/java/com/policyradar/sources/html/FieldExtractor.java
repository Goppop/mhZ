package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行结构化字段提取规则，同时实现 HtmlRuleEvaluator 提供诊断能力。
 *
 * 阶段一改造后，正式抓取路径通过 evaluate/evaluateFirst 获取结果，
 * 预览路径通过 evaluateAll 获取每条规则的诊断。
 * 旧方法保留为 @Deprecated，委托给新方法。
 */
@Slf4j
@Component
public class FieldExtractor implements HtmlRuleEvaluator {

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[年/\\-](\\d{1,2})[月/\\-](\\d{1,2})日?");

    // ──────────────────────── HtmlRuleEvaluator 实现 ────────────────────────

    @Override
    public RuleEvalResult evaluate(Element root, PolicyExtractRule rule) {
        if (root == null || rule == null) {
            return RuleEvalResult.builder()
                    .fieldName(rule != null ? rule.getFieldName() : null)
                    .matched(false)
                    .rule(rule)
                    .diagnostic(RuleDiagnostic.builder()
                            .failureReason("root 或 rule 为 null")
                            .build())
                    .build();
        }

        String valueType = normalize(rule.getValueType());
        String value = null;
        int elementsMatched = 0;
        Integer extractedTextLength = null;
        String failureReason = null;

        try {
            if ("CONST".equals(valueType)) {
                value = rule.getConstValue();
                elementsMatched = 0;
                extractedTextLength = value != null ? value.length() : null;
            } else if ("ATTR".equals(valueType)) {
                if (isBlank(rule.getAttrName())) {
                    failureReason = "ATTR 类型缺少 attrName";
                } else {
                    Elements selected = selectElements(root, rule);
                    elementsMatched = selected.size();
                    if (!selected.isEmpty()) {
                        value = selected.first().attr(rule.getAttrName().trim());
                        extractedTextLength = value != null ? value.length() : null;
                        if (isBlank(value)) {
                            failureReason = "属性 " + rule.getAttrName() + " 的值为空";
                        }
                    } else {
                        failureReason = "selector 未命中任何元素: " + rule.getSelector();
                    }
                }
            } else if ("REGEX".equals(valueType)) {
                Elements selected = selectElements(root, rule);
                elementsMatched = selected.size();
                if (!selected.isEmpty()) {
                    String source = selected.first().text();
                    value = applyRegex(source, rule.getRegexPattern());
                    if (value != null) {
                        extractedTextLength = value.length();
                    } else {
                        failureReason = "正则未匹配: " + rule.getRegexPattern();
                    }
                } else {
                    failureReason = "selector 未命中任何元素: " + rule.getSelector();
                }
            } else {
                // TEXT / HTML / 未知类型 均按 TEXT 处理
                Elements selected = selectElements(root, rule);
                elementsMatched = selected.size();
                if (!selected.isEmpty()) {
                    Element first = selected.first();
                    if ("HTML".equals(valueType)) {
                        value = first.html();
                    } else {
                        value = first.text();
                    }
                    extractedTextLength = value != null ? value.length() : null;
                    if (isBlank(value)) {
                        failureReason = "元素文本为空";
                    }
                } else {
                    failureReason = "selector 未命中任何元素: " + rule.getSelector();
                }
            }

            // 与重构前保持一致：非 REGEX 类型在取值后统一执行 regexPattern 后处理。
            // 若配置了 regexPattern 但未匹配，应视为该规则未命中，以便触发兜底规则。
            if (!"REGEX".equals(valueType) && value != null && !isBlank(rule.getRegexPattern())) {
                String afterRegex = applyRegex(value, rule.getRegexPattern());
                if (afterRegex == null) {
                    value = null;
                    extractedTextLength = null;
                    failureReason = "正则未匹配: " + rule.getRegexPattern();
                } else {
                    value = afterRegex;
                    extractedTextLength = value.length();
                    failureReason = null;
                }
            }
        } catch (Exception e) {
            log.debug("字段提取失败 field={} selector={} type={}",
                    rule.getFieldName(), rule.getSelector(), rule.getValueType(), e);
            failureReason = "提取异常: " + e.getMessage();
        }

        boolean matched = value != null && !value.isEmpty();
        RuleDiagnostic.RuleDiagnosticBuilder diagBuilder = RuleDiagnostic.builder()
                .elementsMatchedBySelector(elementsMatched)
                .extractedTextLength(extractedTextLength)
                .failureReason(failureReason);

        // 日期字段：尝试解析日期
        if ("publishDate".equals(rule.getFieldName()) && value != null && !value.isEmpty()) {
            LocalDate parsed = parseDateInternal(value.trim(), rule);
            if (parsed != null) {
                diagBuilder.dateParseSuccess(true);
                diagBuilder.parsedDate(parsed);
            } else {
                diagBuilder.dateParseSuccess(false);
            }
        }

        return RuleEvalResult.builder()
                .fieldName(rule.getFieldName())
                .value(matched ? value.trim() : null)
                .matched(matched)
                .rule(rule)
                .diagnostic(diagBuilder.build())
                .build();
    }

    @Override
    public RuleEvalResult evaluateFirst(Element root, List<PolicyExtractRule> rules, String fieldName) {
        if (root == null || rules == null || fieldName == null) {
            return RuleEvalResult.builder()
                    .fieldName(fieldName)
                    .matched(false)
                    .diagnostic(RuleDiagnostic.builder()
                            .failureReason("root 或 rules 或 fieldName 为 null")
                            .build())
                    .build();
        }

        for (int i = 0; i < rules.size(); i++) {
            PolicyExtractRule rule = rules.get(i);
            if (rule == null) {
                continue;
            }
            if (!fieldName.equals(rule.getFieldName())) {
                continue;
            }
            RuleEvalResult result = evaluate(root, rule);
            if (result.isMatched()) {
                return result;
            }
        }

        return RuleEvalResult.builder()
                .fieldName(fieldName)
                .matched(false)
                .diagnostic(RuleDiagnostic.builder()
                        .failureReason("字段 " + fieldName + " 的所有规则均未命中")
                        .build())
                .build();
    }

    @Override
    public List<RuleEvalResult> evaluateAll(Element root, List<PolicyExtractRule> rules, String fieldName) {
        List<RuleEvalResult> results = new ArrayList<>();
        if (root == null || rules == null || fieldName == null) {
            return results;
        }

        for (int i = 0; i < rules.size(); i++) {
            PolicyExtractRule rule = rules.get(i);
            if (rule == null) {
                continue;
            }
            if (!fieldName.equals(rule.getFieldName())) {
                continue;
            }
            results.add(evaluate(root, rule));
        }
        return results;
    }

    // ──────────────────── 旧 API（@Deprecated，委托给新方法） ────────────────────

    /**
     * @deprecated 使用 {@link #evaluateFirst(Element, List, String)} 替代。
     *             返回 RuleEvalResult 可获取诊断信息。
     */
    @Deprecated
    public Optional<ExtractedField> extractFirst(Element root, String fieldName, List<PolicyExtractRule> rules) {
        RuleEvalResult result = evaluateFirst(root, rules, fieldName);
        if (result.isMatched()) {
            return Optional.of(ExtractedField.builder()
                    .fieldName(fieldName)
                    .value(result.getValue())
                    .rule(result.getRule())
                    .build());
        }
        return Optional.empty();
    }

    /**
     * @deprecated 使用 {@link #evaluate(Element, PolicyExtractRule)} 替代。
     *             返回 RuleEvalResult 可获取诊断信息。
     */
    @Deprecated
    public String extract(Element root, PolicyExtractRule rule) {
        RuleEvalResult result = evaluate(root, rule);
        return result.getValue();
    }

    /**
     * @deprecated 使用 {@link RuleDiagnostic#getParsedDate()} 从诊断结果中获取解析后的日期。
     */
    @Deprecated
    public LocalDate parseDate(String value, PolicyExtractRule rule) {
        return parseDateInternal(value, rule);
    }

    // ──────────────────────── 内部实现 ────────────────────────

    /**
     * 日期解析内部实现。
     */
    private LocalDate parseDateInternal(String value, PolicyExtractRule rule) {
        if (isBlank(value)) {
            return null;
        }

        String text = value.trim();
        if (rule != null && !isBlank(rule.getDateFormat())) {
            LocalDate parsed = tryParse(text, rule.getDateFormat().trim());
            if (parsed != null) {
                return parsed;
            }
        }

        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy年M月d日", "yyyy-M-d", "yyyy/M/d"};
        for (int i = 0; i < patterns.length; i++) {
            LocalDate parsed = tryParse(text, patterns[i]);
            if (parsed != null) {
                return parsed;
            }
        }

        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                );
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    /**
     * 根据规则选择元素。selector 为空时返回仅含 root 的集合。
     */
    private Elements selectElements(Element root, PolicyExtractRule rule) {
        if (isBlank(rule.getSelector())) {
            Elements elements = new Elements();
            elements.add(root);
            return elements;
        }
        return root.select(rule.getSelector().trim());
    }

    private LocalDate tryParse(String value, String pattern) {
        try {
            return LocalDate.parse(value.replaceAll("[\\s　]", ""), DateTimeFormatter.ofPattern(pattern));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String applyRegex(String value, String regexPattern) {
        if (isBlank(value) || isBlank(regexPattern)) {
            return value;
        }

        Matcher matcher = Pattern.compile(regexPattern).matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
    }

    private String normalize(String value) {
        return value == null ? "TEXT" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}