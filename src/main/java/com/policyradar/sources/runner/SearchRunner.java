package com.policyradar.sources.runner;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.policyradar.frontier.FrontierWriter;
import com.policyradar.infra.CrawlProperties;
import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.entity.PolicyKeyword;
import com.policyradar.persistence.mapper.PolicyKeywordMapper;
import com.policyradar.search.SearchContext;
import com.policyradar.search.SearchProvider;
import com.policyradar.search.UrlCandidate;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.SourceRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 搜索驱动型数据源执行器
 *
 * type = "SEARCH"
 *
 * <p>流程：读 policy_data_source.config 确定检索器和关键词组
 * → 调 SearchProvider.search() → 候选 URL 写入 policy_url_frontier
 * → 返回空列表（详情由 FrontierConsumer 异步抓取）</p>
 *
 * <p>config 格式：
 * <pre>
 * {
 *   "provider": "gov_cn",
 *   "keyword_ids": [1, 2],
 *   "since_days": 7,
 *   "max_per_keyword": 30
 * }
 * </pre>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRunner implements SourceRunner {

    private final List<SearchProvider> providers;
    private final PolicyKeywordMapper keywordMapper;
    private final FrontierWriter frontierWriter;
    private final CrawlProperties crawlProps;

    /** 懒加载 provider map */
    private volatile Map<String, SearchProvider> providerMap;

    @Override
    public String type() {
        return "SEARCH";
    }

    @Override
    public List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context) {
        SearchConfig cfg = parseConfig(dataSource.getConfig());
        if (cfg == null || cfg.provider == null) {
            log.error("[SearchRunner] 数据源 {} config 缺少 provider 字段", dataSource.getName());
            return Collections.emptyList();
        }

        SearchProvider provider = getProvider(cfg.provider);
        if (provider == null) {
            log.error("[SearchRunner] 未找到 SearchProvider: {}，已注册: {}",
                    cfg.provider, getProviderMap().keySet());
            return Collections.emptyList();
        }

        String ua = crawlProps.getUserAgents().isEmpty() ? null : crawlProps.getUserAgents().get(0);
        SearchContext ctx = SearchContext.builder()
                .sinceDays(cfg.sinceDays)
                .maxResults(cfg.maxPerKeyword)
                .userAgent(ua)
                .build();

        int totalQueued = 0;
        for (Long keywordId : cfg.keywordIds) {
            PolicyKeyword keyword = keywordMapper.selectById(keywordId);
            if (keyword == null || !Boolean.TRUE.equals(keyword.getEnabled())) {
                log.debug("[SearchRunner] 关键词 {} 不存在或未启用，跳过", keywordId);
                continue;
            }
            if (!Boolean.TRUE.equals(keyword.getSearchEnabled())) {
                log.debug("[SearchRunner] 关键词「{}」search_enabled=false，跳过主动检索", keyword.getName());
                continue;
            }

            try {
                List<UrlCandidate> candidates = provider.search(keyword, ctx);
                int queued = frontierWriter.writeAll(candidates, dataSource.getId());
                totalQueued += queued;
                log.info("[SearchRunner] 关键词「{}」→ provider={} → 入队 {} 条候选",
                        keyword.getName(), cfg.provider, queued);
            } catch (Exception e) {
                log.error("[SearchRunner] 关键词「{}」搜索失败: {}", keyword.getName(), e.getMessage(), e);
            }
        }

        log.info("[SearchRunner] 数据源「{}」本次共入队 {} 条候选 URL", dataSource.getName(), totalQueued);
        // SearchRunner 本身不产出 RawDoc，详情异步由 FrontierConsumer 处理
        return Collections.emptyList();
    }

    private SearchProvider getProvider(String name) {
        return getProviderMap().get(name);
    }

    private Map<String, SearchProvider> getProviderMap() {
        if (providerMap == null) {
            synchronized (this) {
                if (providerMap == null) {
                    providerMap = providers.stream()
                            .collect(Collectors.toMap(SearchProvider::name, Function.identity()));
                }
            }
        }
        return providerMap;
    }

    private SearchConfig parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return new SearchConfig();
        try {
            JSONObject obj = JSON.parseObject(configJson);
            SearchConfig cfg = new SearchConfig();
            cfg.provider = obj.getString("provider");
            cfg.sinceDays = obj.getIntValue("since_days", 7);
            cfg.maxPerKeyword = obj.getIntValue("max_per_keyword", 30);
            JSONArray arr = obj.getJSONArray("keyword_ids");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    cfg.keywordIds.add(arr.getLong(i));
                }
            }
            return cfg;
        } catch (Exception e) {
            log.error("[SearchRunner] config 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static class SearchConfig {
        String provider;
        int sinceDays = 7;
        int maxPerKeyword = 30;
        List<Long> keywordIds = new java.util.ArrayList<>();
    }
}
