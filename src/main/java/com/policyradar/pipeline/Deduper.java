package com.policyradar.pipeline;

import com.policyradar.persistence.entity.PolicyDocument;
import com.policyradar.persistence.mapper.PolicyDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 去重组件
 *
 * 实现双层去重策略：
 * 1. URL 唯一去重（UK 索引兜底）
 * 2. 内容哈希去重（SHA1 title|publishDate|content）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Deduper {

    private final PolicyDocumentMapper documentMapper;

    /**
     * 对 PolicyDocument 列表进行去重
     *
     * @param documents 需要去重的文档列表
     * @return 去重后的文档列表
     */
    public List<PolicyDocument> deduplicate(List<PolicyDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<PolicyDocument> uniqueDocs = new ArrayList<>();
        Set<String> existingUrls = getExistingUrls();
        Set<String> existingContentHashes = getExistingContentHashes();
        Set<String> processedUrls = new HashSet<>();
        Set<String> processedContentHashes = new HashSet<>();

        int urlDupCount = 0;
        int contentDupCount = 0;

        for (PolicyDocument doc : documents) {
            // URL 去重
            if (doc.getUrl() != null && existingUrls.contains(doc.getUrl())) {
                urlDupCount++;
                log.debug("URL 重复，已过滤: {}", doc.getUrl());
                continue;
            }

            // 内容哈希去重
            if (doc.getContentHash() != null && existingContentHashes.contains(doc.getContentHash())) {
                contentDupCount++;
                log.debug("内容重复，已过滤: {}", doc.getTitle());
                continue;
            }

            // 内存去重（防止同批次内部重复）
            if (processedUrls.contains(doc.getUrl())) {
                log.debug("同批次 URL 重复，已过滤: {}", doc.getUrl());
                continue;
            }
            if (processedContentHashes.contains(doc.getContentHash())) {
                log.debug("同批次内容重复，已过滤: {}", doc.getTitle());
                continue;
            }

            // 标记为已处理
            processedUrls.add(doc.getUrl());
            processedContentHashes.add(doc.getContentHash());
            uniqueDocs.add(doc);
        }

        log.info("去重完成：总 {} 条 → 保留 {} 条，URL 重复 {} 条，内容重复 {} 条",
                documents.size(), uniqueDocs.size(), urlDupCount, contentDupCount);

        return uniqueDocs;
    }

    /**
     * 获取数据库中已存在的 URL 集合
     */
    private Set<String> getExistingUrls() {
        try {
            // 这里应该从数据库查询，使用 LIMIT 防止内存溢出
            List<PolicyDocument> existing = documentMapper.selectList(null);
            return existing.stream()
                    .map(PolicyDocument::getUrl)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("获取已存在 URL 失败", e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取数据库中已存在的内容哈希集合
     */
    private Set<String> getExistingContentHashes() {
        try {
            List<PolicyDocument> existing = documentMapper.selectList(null);
            return existing.stream()
                    .map(PolicyDocument::getContentHash)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("获取已存在内容哈希失败", e);
            return Collections.emptySet();
        }
    }

    /**
     * 增量去重（只检查新增的文档）
     * 用于增量抓取场景的快速去重
     */
    public List<PolicyDocument> deduplicateIncremental(List<PolicyDocument> newDocuments,
                                                      List<PolicyDocument> existingDocuments) {
        if (newDocuments == null || newDocuments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> existingUrls = existingDocuments.stream()
                .map(PolicyDocument::getUrl)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> existingContentHashes = existingDocuments.stream()
                .map(PolicyDocument::getContentHash)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return newDocuments.stream()
                .filter(doc -> doc.getUrl() != null && !existingUrls.contains(doc.getUrl()))
                .filter(doc -> doc.getContentHash() != null && !existingContentHashes.contains(doc.getContentHash()))
                .collect(Collectors.toList());
    }
}