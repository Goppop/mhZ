package com.policyradar.sources.html;

import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.sources.RawDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExtractedItem → RawDoc 字段映射器。
 *
 * 字段名到 RawDoc setter 的映射集中此处，不散落在 HtmlRunner 各处。
 * 未识别的字段名自动进入 metadata。
 */
@Slf4j
@Component
public class RawDocMapper {

    /**
     * 将列表页提取结果映射为 RawDoc。
     *
     * @param ds   数据源配置
     * @param item 列表页提取结果
     * @return RawDoc 对象
     */
    public RawDoc fromListItem(PolicyDataSource ds, ExtractedItem item) {
        if (ds == null || item == null) {
            return null;
        }

        RawDoc raw = RawDoc.builder()
                .source(ds.getName())
                .metadata(new LinkedHashMap<>())
                .build();

        applyFields(raw, item);
        return raw;
    }

    /**
     * 将详情页提取结果合并到已有 RawDoc。
     * 仅在详情字段非空时覆盖，空值不覆盖列表已有值。
     *
     * @param raw    已有 RawDoc
     * @param detail 详情页提取结果
     */
    public void mergeDetail(RawDoc raw, ExtractedItem detail) {
        if (raw == null || detail == null) {
            return;
        }

        Map<String, String> detailFields = detail.getFields();
        if (detailFields == null || detailFields.isEmpty()) {
            return;
        }

        Map<String, RuleEvalResult> detailResults = detail.getDetails();

        for (Map.Entry<String, String> entry : detailFields.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }

            RuleEvalResult evalResult = null;
            if (detailResults != null) {
                evalResult = detailResults.get(fieldName);
            }

            applySingleField(raw, fieldName, value, evalResult);
        }
    }

    /**
     * 遍历 ExtractedItem 的字段，映射到 RawDoc。
     */
    private void applyFields(RawDoc raw, ExtractedItem item) {
        Map<String, String> fields = item.getFields();
        if (fields == null || fields.isEmpty()) {
            return;
        }

        Map<String, RuleEvalResult> details = item.getDetails();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            RuleEvalResult evalResult = null;
            if (details != null) {
                evalResult = details.get(fieldName);
            }

            applySingleField(raw, fieldName, value, evalResult);
        }
    }

    /**
     * 单字段映射到 RawDoc。
     */
    private void applySingleField(RawDoc raw, String fieldName, String value, RuleEvalResult evalResult) {
        if (fieldName == null || value == null || value.isEmpty()) {
            return;
        }

        switch (fieldName) {
            case "title":
                raw.setTitle(value);
                break;
            case "url":
                raw.setUrl(value);
                break;
            case "source":
                raw.setSource(value);
                break;
            case "summary":
                raw.setSummary(value);
                break;
            case "content":
                raw.setContent(value);
                break;
            case "issuingAgency":
                raw.setIssuingAgency(value);
                break;
            case "documentNumber":
                raw.setDocumentNumber(value);
                break;
            case "publishDate":
                // 优先从诊断结果取解析后的日期
                if (evalResult != null
                        && evalResult.getDiagnostic() != null
                        && evalResult.getDiagnostic().getParsedDate() != null) {
                    raw.setPublishDate(evalResult.getDiagnostic().getParsedDate());
                } else {
                    // 兜底：尝试解析
                    LocalDate parsed = parseDateSimple(value);
                    if (parsed != null) {
                        raw.setPublishDate(parsed);
                    }
                }
                break;
            default:
                putMetadata(raw, fieldName, value);
                break;
        }
    }

    private void putMetadata(RawDoc raw, String fieldName, String value) {
        if (raw.getMetadata() == null) {
            raw.setMetadata(new LinkedHashMap<>());
        }
        raw.getMetadata().put(fieldName, value);
    }

    /**
     * 兜底日期解析（当 diagnostic 中没有 parsedDate 时使用）。
     */
    private LocalDate parseDateSimple(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            String cleaned = value.trim().replaceAll("[\\s　]", "");
            String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy年M月d日", "yyyy-M-d", "yyyy/M/d"};
            for (int i = 0; i < patterns.length; i++) {
                try {
                    return LocalDate.parse(cleaned, java.time.format.DateTimeFormatter.ofPattern(patterns[i]));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("日期解析失败: {}", value);
        }
        return null;
    }
}