package com.policyradar.pipeline;

import com.policyradar.persistence.entity.PolicyDocument;
import com.policyradar.persistence.entity.PolicyKeyword;
import com.policyradar.persistence.mapper.PolicyKeywordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词匹配组件
 *
 * 对文档进行关键词匹配（使用简单的字符串匹配）
 * 支持包含、排除等复杂逻辑
 */
@Slf4j
@Component
public class KeywordMatcher {

    private final PolicyKeywordMapper keywordMapper;

    // 关键词集合
    private volatile Map<String, PolicyKeyword> keywordMap;
    // 最后重建时间
    private volatile long lastRebuildTime = 0;
    // 重建间隔（1分钟）
    private static final long REBUILD_INTERVAL_MS = 60_000;

    public KeywordMatcher(PolicyKeywordMapper keywordMapper) {
        this.keywordMapper = keywordMapper;
    }

    /**
     * 初始化时构建关键词集合
     */
    @PostConstruct
    public void init() {
        rebuildKeywords();
    }

    /**
     * 重建关键词集合
     */
    public synchronized void rebuildKeywords() {
        log.info("重建关键词集合...");
        try {
            List<PolicyKeyword> keywords = keywordMapper.selectList(null);

            if (keywords == null || keywords.isEmpty()) {
                log.warn("没有配置关键词，跳过关键词集合构建");
                this.keywordMap = Collections.emptyMap();
                return;
            }

            // 过滤未启用的关键词
            List<PolicyKeyword> enabledKeywords = keywords.stream()
                    .filter(k -> k.getEnabled() == null || k.getEnabled())
                    .collect(Collectors.toList());

            if (enabledKeywords.isEmpty()) {
                log.warn("没有启用的关键词，跳过关键词集合构建");
                this.keywordMap = Collections.emptyMap();
                return;
            }

            // 构建关键词映射
            Map<String, PolicyKeyword> newKeywordMap = new HashMap<>();

            for (PolicyKeyword keyword : enabledKeywords) {
                if (keyword.getKeyword() != null && !keyword.getKeyword().isEmpty()) {
                    String kw = keyword.getKeyword().trim();
                    newKeywordMap.put(kw, keyword);

                    // 添加同义词（如果有）
                    if (keyword.getIncludeAny() != null && !keyword.getIncludeAny().isEmpty()) {
                        String[] synonyms = keyword.getIncludeAny().split(",");
                        for (String syn : synonyms) {
                            String synTrimmed = syn.trim();
                            if (!synTrimmed.isEmpty() && !synTrimmed.equals(kw)) {
                                newKeywordMap.put(synTrimmed, keyword);
                            }
                        }
                    }
                }
            }

            this.keywordMap = Collections.unmodifiableMap(newKeywordMap);
            this.lastRebuildTime = System.currentTimeMillis();
            log.info("关键词集合构建完成，共 {} 个关键词（含同义词）", newKeywordMap.size());

        } catch (Exception e) {
            log.error("重建关键词集合失败", e);
        }
    }

    /**
     * 匹配文档，返回命中的关键词列表
     *
     * @param document 需要匹配的文档
     * @return 命中的关键词列表
     */
    public List<MatchResult> match(PolicyDocument document) {
        if (keywordMap == null || keywordMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 检查是否需要重建关键词集合
        checkAndRebuildIfNeeded();

        List<MatchResult> results = new ArrayList<>();

        // 拼接所有需要匹配的文本
        String textToMatch = buildTextToMatch(document);

        if (textToMatch.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用简单的字符串匹配
        Set<String> matchedKeywords = new HashSet<>();
        for (Map.Entry<String, PolicyKeyword> entry : keywordMap.entrySet()) {
            String keyword = entry.getKey();
            if (keyword != null && !keyword.isEmpty() && textToMatch.contains(keyword)) {
                if (!matchedKeywords.contains(keyword)) {
                    matchedKeywords.add(keyword);
                    PolicyKeyword keywordConfig = entry.getValue();
                    if (keywordConfig != null) {
                        // 检查匹配逻辑
                        if (checkMatchLogic(document, keywordConfig)) {
                            results.add(new MatchResult(keywordConfig, keyword));
                            log.debug("文档命中关键词: {} - {}", document.getTitle(), keyword);
                        }
                    }
                }
            }
        }

        return results;
    }

    /**
     * 批量匹配文档
     */
    public Map<PolicyDocument, List<MatchResult>> matchBatch(List<PolicyDocument> documents) {
        Map<PolicyDocument, List<MatchResult>> resultMap = new HashMap<>();
        for (PolicyDocument doc : documents) {
            resultMap.put(doc, match(doc));
        }
        return resultMap;
    }

    /**
     * 构建需要匹配的文本
     */
    private String buildTextToMatch(PolicyDocument document) {
        StringBuilder sb = new StringBuilder();
        if (document.getTitle() != null) {
            sb.append(document.getTitle()).append("\n");
        }
        if (document.getSummary() != null) {
            sb.append(document.getSummary()).append("\n");
        }
        if (document.getContent() != null) {
            sb.append(document.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 检查匹配逻辑（包含/排除条件）
     */
    private boolean checkMatchLogic(PolicyDocument document, PolicyKeyword keyword) {
        String text = buildTextToMatch(document);

        // 包含全部条件
        if (keyword.getIncludeAll() != null && !keyword.getIncludeAll().isEmpty()) {
            String[] mustHave = keyword.getIncludeAll().split(",");
            for (String s : mustHave) {
                if (!s.trim().isEmpty() && !text.contains(s.trim())) {
                    return false;
                }
            }
        }

        // 排除任意条件
        if (keyword.getExcludeAny() != null && !keyword.getExcludeAny().isEmpty()) {
            String[] mustNotHave = keyword.getExcludeAny().split(",");
            for (String s : mustNotHave) {
                if (!s.trim().isEmpty() && text.contains(s.trim())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 检查并重建关键词集合（如果需要）
     */
    private void checkAndRebuildIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRebuildTime > REBUILD_INTERVAL_MS) {
            rebuildKeywords();
        }
    }

    /**
     * 匹配结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MatchResult {
        private PolicyKeyword keyword;
        private String matchedText;
    }
}