package com.policyradar.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyradar.sources.RawDoc;
import com.policyradar.persistence.entity.PolicyDocument;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 标准化组件
 *
 * 负责对 RawDoc 进行标准化处理：
 * 1. HTML 标签去除
 * 2. 日期格式规范化
 * 3. 机构名同义词归一
 * 4. 文本清洗
 */
@Slf4j
@Component
public class Normalizer {

    private final ObjectMapper objectMapper;

    public Normalizer() {
        this.objectMapper = new ObjectMapper();
    }

    // 日期格式模式
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    );

    // 机构名同义词映射
    private static final Map<String, String> AGENCY_SYNONYMS = new HashMap<>();

    static {
        // 常见政府机构同义词
        AGENCY_SYNONYMS.put("国务院办公厅", "国务院");
        AGENCY_SYNONYMS.put("国家发展和改革委员会", "发改委");
        AGENCY_SYNONYMS.put("中华人民共和国国家发展和改革委员会", "发改委");
        AGENCY_SYNONYMS.put("工业和信息化部", "工信部");
        AGENCY_SYNONYMS.put("中华人民共和国工业和信息化部", "工信部");
        AGENCY_SYNONYMS.put("科学技术部", "科技部");
        AGENCY_SYNONYMS.put("人力资源和社会保障部", "人社部");
        AGENCY_SYNONYMS.put("生态环境部", "环保部");
        AGENCY_SYNONYMS.put("国家生态环境部", "环保部");
        AGENCY_SYNONYMS.put("国家市场监督管理总局", "市场监管总局");
        AGENCY_SYNONYMS.put("国家市场监督管理局", "市场监管总局");
        AGENCY_SYNONYMS.put("国家药品监督管理局", "国家药监局");
        AGENCY_SYNONYMS.put("中国证券监督管理委员会", "证监会");
        AGENCY_SYNONYMS.put("中国银行业监督管理委员会", "银监会");
        AGENCY_SYNONYMS.put("中国保险监督管理委员会", "保监会");
    }

    // 多余空格模式
    private static final Pattern EXTRA_SPACES_PATTERN = Pattern.compile("\\s+");
    // 无意义字符模式
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");

    /**
     * 对 RawDoc 进行标准化处理
     *
     * @param rawDocs 原始文档列表
     * @return 标准化后的 PolicyDocument 列表
     */
    public List<PolicyDocument> normalize(List<RawDoc> rawDocs) {
        return rawDocs.stream()
                .map(this::normalizeSingle)
                .filter(doc -> doc != null && doc.getTitle() != null && !doc.getTitle().isEmpty())
                .collect(Collectors.toList());
    }

    private PolicyDocument normalizeSingle(RawDoc rawDoc) {
        try {
            PolicyDocument doc = new PolicyDocument();

            // 标题
            doc.setTitle(normalizeText(rawDoc.getTitle()));

            // URL
            doc.setUrl(rawDoc.getUrl());

            // 来源
            doc.setSource(normalizeText(rawDoc.getSource()));

            // 发布日期
            doc.setPublishDate(normalizeDate(rawDoc.getPublishDate()));

            // 摘要
            doc.setSummary(normalizeText(rawDoc.getSummary()));

            // 内容（去除 HTML 标签）
            doc.setContent(normalizeContent(rawDoc.getContent()));

            // 发布机构（同义词归一）
            doc.setIssuingAgency(normalizeAgencyName(rawDoc.getIssuingAgency()));

            // 文号
            doc.setDocumentNumber(normalizeText(rawDoc.getDocumentNumber()));

            // 元数据
            try {
                doc.setMetadata(rawDoc.getMetadata() != null ?
                        objectMapper.writeValueAsString(rawDoc.getMetadata()) : null);
            } catch (Exception e) {
                log.warn("元数据序列化失败: {}", rawDoc.getUrl(), e);
                doc.setMetadata(null);
            }

            // 计算内容哈希（去重用）
            doc.setContentHash(calculateContentHash(doc));

            return doc;

        } catch (Exception e) {
            log.error("文档标准化失败: {} - {}", rawDoc.getUrl(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 归一化文本
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = text.strip();
        // 去除首尾的特殊字符
        normalized = normalized.replaceAll("^[\\p{P}\\p{Z}]+|[\\p{P}\\p{Z}]+$", "");
        // 去除多余空格
        normalized = EXTRA_SPACES_PATTERN.matcher(normalized).replaceAll(" ");
        // 去除不可见字符
        normalized = INVALID_CHARS_PATTERN.matcher(normalized).replaceAll("");
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 归一化内容（去除 HTML 标签）
     */
    private String normalizeContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        // 使用 Jsoup 去除 HTML 标签
        String plainText = Jsoup.clean(content, Safelist.none());
        return normalizeText(plainText);
    }

    /**
     * 归一化发布日期
     */
    private LocalDate normalizeDate(LocalDate publishDate) {
        if (publishDate == null) {
            return null;
        }
        // 确保日期的有效性
        return publishDate.isAfter(LocalDate.now().plusYears(10)) ? null : publishDate;
    }

    /**
     * 归一化发布机构（同义词映射）
     */
    private String normalizeAgencyName(String agencyName) {
        if (agencyName == null || agencyName.isEmpty()) {
            return null;
        }
        String normalized = normalizeText(agencyName);
        if (normalized == null) {
            return null;
        }
        // 同义词归一
        for (Map.Entry<String, String> entry : AGENCY_SYNONYMS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                log.debug("机构名归一化: {} → {}", normalized, entry.getValue());
                return entry.getValue();
            }
        }
        return normalized;
    }


    /**
     * 计算内容哈希
     */
    private String calculateContentHash(PolicyDocument doc) {
        String contentToHash = String.format(
                "%s|%s|%s",
                doc.getTitle() != null ? doc.getTitle() : "",
                doc.getPublishDate() != null ? doc.getPublishDate().toString() : "",
                doc.getContent() != null ? doc.getContent() : ""
        );
        return org.apache.commons.codec.digest.DigestUtils.sha1Hex(contentToHash);
    }
}