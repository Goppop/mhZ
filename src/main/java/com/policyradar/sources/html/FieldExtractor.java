package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyExtractRule;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行结构化字段提取规则。
 */
@Slf4j
@Component
public class FieldExtractor {

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[年/\\-](\\d{1,2})[月/\\-](\\d{1,2})日?");

    public Optional<ExtractedField> extractFirst(Element root, String fieldName, List<PolicyExtractRule> rules) {
        for (PolicyExtractRule rule : rules) {
            if (!fieldName.equals(rule.getFieldName())) {
                continue;
            }
            String value = extract(root, rule);
            if (!isBlank(value)) {
                return Optional.of(ExtractedField.builder()
                        .fieldName(fieldName)
                        .value(value.trim())
                        .rule(rule)
                        .build());
            }
        }
        return Optional.empty();
    }

    public String extract(Element root, PolicyExtractRule rule) {
        if (root == null || rule == null) {
            return null;
        }

        try {
            String valueType = normalize(rule.getValueType());
            String value = switch (valueType) {
                case "ATTR" -> extractAttr(root, rule);
                case "HTML" -> target(root, rule).map(Element::html).orElse(null);
                case "CONST" -> rule.getConstValue();
                case "REGEX" -> extractByRegex(root, rule);
                case "TEXT" -> target(root, rule).map(Element::text).orElse(null);
                default -> target(root, rule).map(Element::text).orElse(null);
            };

            return "REGEX".equals(valueType) ? value : applyRegex(value, rule.getRegexPattern());
        } catch (Exception e) {
            log.debug("字段提取失败 field={} selector={} type={}",
                    rule.getFieldName(), rule.getSelector(), rule.getValueType(), e);
            return null;
        }
    }

    public LocalDate parseDate(String value, PolicyExtractRule rule) {
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

        for (String pattern : List.of("yyyy-MM-dd", "yyyy/MM/dd", "yyyy年M月d日", "yyyy-M-d", "yyyy/M/d")) {
            LocalDate parsed = tryParse(text, pattern);
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

    private Optional<Element> target(Element root, PolicyExtractRule rule) {
        if (isBlank(rule.getSelector())) {
            return Optional.of(root);
        }

        Elements selected = root.select(rule.getSelector().trim());
        return selected.isEmpty() ? Optional.empty() : Optional.of(selected.first());
    }

    private String extractAttr(Element root, PolicyExtractRule rule) {
        if (isBlank(rule.getAttrName())) {
            return null;
        }
        return target(root, rule)
                .map(element -> element.attr(rule.getAttrName().trim()))
                .orElse(null);
    }

    private String extractByRegex(Element root, PolicyExtractRule rule) {
        String source = target(root, rule).map(Element::text).orElse(null);
        return applyRegex(source, rule.getRegexPattern());
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

    private LocalDate tryParse(String value, String pattern) {
        try {
            return LocalDate.parse(value.replaceAll("[\\s　]", ""), DateTimeFormatter.ofPattern(pattern));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "TEXT" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
