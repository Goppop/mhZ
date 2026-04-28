package com.policyradar.frontier;

import com.policyradar.persistence.entity.PolicyUrlFrontier;
import com.policyradar.persistence.mapper.PolicyUrlFrontierMapper;
import com.policyradar.search.UrlCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 候选 URL 入队写入器
 *
 * 把 SearchProvider 返回的 UrlCandidate 写入 policy_url_frontier，
 * 通过 url_hash UNIQUE 约束自动去重（重复 URL 静默忽略）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrontierWriter {

    private final PolicyUrlFrontierMapper frontierMapper;

    /**
     * 批量写入候选 URL，返回实际入队数量（重复项不计）
     */
    public int writeAll(List<UrlCandidate> candidates, Long dataSourceId) {
        if (candidates == null || candidates.isEmpty()) return 0;
        int written = 0;
        for (UrlCandidate c : candidates) {
            if (write(c, dataSourceId)) written++;
        }
        return written;
    }

    /**
     * 写入单条候选，重复则忽略返回 false
     */
    public boolean write(UrlCandidate candidate, Long dataSourceId) {
        if (candidate.getUrl() == null || candidate.getUrl().isBlank()) return false;

        String hash = DigestUtils.sha1Hex(candidate.getUrl().trim());

        // 快速判断是否已存在
        if (frontierMapper.countByUrlHash(hash) > 0) {
            log.debug("[FrontierWriter] 已存在，跳过: {}", candidate.getUrl());
            return false;
        }

        PolicyUrlFrontier entity = new PolicyUrlFrontier();
        entity.setUrl(candidate.getUrl().trim());
        entity.setUrlHash(hash);
        entity.setDataSourceId(dataSourceId);
        entity.setKeywordId(candidate.getKeywordId());
        entity.setProvider(candidate.getProvider());
        entity.setTitleSnippet(truncate(candidate.getTitleSnippet(), 490));
        entity.setStatus("PENDING");
        entity.setRetryCount(0);

        try {
            frontierMapper.insert(entity);
            log.debug("[FrontierWriter] 入队: {}", candidate.getUrl());
            return true;
        } catch (Exception e) {
            // 并发场景下 UNIQUE 冲突视为正常
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                log.debug("[FrontierWriter] 并发重复，忽略: {}", candidate.getUrl());
            } else {
                log.warn("[FrontierWriter] 写入失败: {} - {}", candidate.getUrl(), e.getMessage());
            }
            return false;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
