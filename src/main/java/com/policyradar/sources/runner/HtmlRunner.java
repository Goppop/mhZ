package com.policyradar.sources.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.entity.PolicyExtractRule;
import com.policyradar.persistence.entity.PolicyPaginationRule;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.SourceRunner;
import com.policyradar.sources.html.ExtractedItem;
import com.policyradar.sources.html.HtmlCrawlConfig;
import com.policyradar.sources.html.HtmlCrawlConfigLoader;
import com.policyradar.sources.html.HtmlExtractionEngine;
import com.policyradar.sources.html.HtmlPageResponse;
import com.policyradar.sources.html.HtmlRequestClient;
import com.policyradar.sources.html.RawDocMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * HTML 静态页源执行器。
 *
 * 负责编排抓取流程（加载配置、构建分页 URL、调度并发、组装 RawDoc、增量过滤），
 * 所有字段提取和 URL 补全已下沉到 HtmlExtractionEngine 和 HtmlRuleEvaluator。
 */
@Slf4j
@Component
public class HtmlRunner implements SourceRunner {

    private final HtmlCrawlConfigLoader configLoader;
    private final HtmlRequestClient requestClient;
    private final HtmlExtractionEngine extractionEngine;
    private final RawDocMapper rawDocMapper;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor crawlerThreadPool;

    public HtmlRunner(HtmlCrawlConfigLoader configLoader,
                      HtmlRequestClient requestClient,
                      HtmlExtractionEngine extractionEngine,
                      RawDocMapper rawDocMapper,
                      ObjectMapper objectMapper,
                      @Qualifier("crawlerThreadPool") ThreadPoolTaskExecutor crawlerThreadPool) {
        this.configLoader = configLoader;
        this.requestClient = requestClient;
        this.extractionEngine = extractionEngine;
        this.rawDocMapper = rawDocMapper;
        this.objectMapper = objectMapper;
        this.crawlerThreadPool = crawlerThreadPool;
    }

    @Override
    public String type() {
        return "HTML";
    }

    /**
     * 执行 HTML 数据源抓取入口。
     *
     * @param dataSource 数据源配置
     * @param context    抓取上下文，包含上次抓取时间等信息
     * @return 抓取到的原始文档列表
     */
    @Override
    public List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context) {
        HtmlCrawlConfig config = configLoader.load(dataSource.getId());
        if (!config.hasStructuredConfig()) {
            log.warn("HTML 源 {} 未配置结构化规则，跳过抓取", dataSource.getName());
            return Collections.emptyList();
        }

        List<String> listUrls = buildListUrls(config);
        if (listUrls.isEmpty()) {
            return Collections.emptyList();
        }

        // 列表页级并发
        List<CompletableFuture<List<RawDoc>>> pageFutures = new ArrayList<>();
        for (int i = 0; i < listUrls.size(); i++) {
            final String url = listUrls.get(i);
            CompletableFuture<List<RawDoc>> future = CompletableFuture.supplyAsync(
                    new Supplier<List<RawDoc>>() {
                        @Override
                        public List<RawDoc> get() {
                            return fetchPage(dataSource, config, url, context);
                        }
                    },
                    crawlerThreadPool
            );
            pageFutures.add(future);
        }

        List<RawDoc> allDocs = new ArrayList<>();
        for (int i = 0; i < pageFutures.size(); i++) {
            List<RawDoc> pageDocs = safeJoin(pageFutures.get(i));
            if (pageDocs != null) {
                for (int j = 0; j < pageDocs.size(); j++) {
                    allDocs.add(pageDocs.get(j));
                }
            }
        }

        log.debug("HTML 源 {} 抓取成功，获取到 {} 条新文档",
                dataSource.getName(), allDocs.size());
        return allDocs;
    }

    // ──────────────────────── 单页抓取 ────────────────────────

    /**
     * 抓取单个列表页：请求页面 → 提取 items → 逐 item 并发处理。
     */
    private List<RawDoc> fetchPage(PolicyDataSource ds, HtmlCrawlConfig config,
                                    String listUrl, FetchContext ctx) {
        Map<String, String> headers = parseHeaders(config.getListPage().getHeaders());
        HtmlPageResponse page = requestClient.fetch(listUrl, headers, config.getListTimeoutMs());
        if (!page.isSuccess()) {
            log.warn("HTML 源 {} 列表页请求失败: {} status={} error={}",
                    ds.getName(), listUrl, page.getStatusCode(), page.getError());
            return Collections.emptyList();
        }

        List<PolicyExtractRule> listRules = config.getListRules();
        if (listRules.isEmpty()) {
            log.warn("HTML 源 {} 列表页无提取规则，跳过", ds.getName());
            return Collections.emptyList();
        }

        List<ExtractedItem> items = extractionEngine.extractList(
                page.getHtml(), config.getListPage().getItemSelector(), listRules, listUrl);

        log.info("HTML 源 {} 列表页 {} 命中 {} 个 item", ds.getName(), listUrl, items.size());

        // item 级并发
        List<CompletableFuture<RawDoc>> itemFutures = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final ExtractedItem item = items.get(i);
            CompletableFuture<RawDoc> future = CompletableFuture.supplyAsync(
                    new Supplier<RawDoc>() {
                        @Override
                        public RawDoc get() {
                            return processItem(ds, config, item, ctx);
                        }
                    },
                    crawlerThreadPool
            );
            itemFutures.add(future);
        }

        List<RawDoc> pageDocs = new ArrayList<>();
        for (int i = 0; i < itemFutures.size(); i++) {
            RawDoc doc = safeJoinItem(itemFutures.get(i));
            if (doc != null) {
                pageDocs.add(doc);
            }
        }

        return pageDocs;
    }

    // ──────────────────────── 单 item 处理 ────────────────────────

    /**
     * 处理单个列表项：映射 → 日期过滤 → 详情补充 → 二次过滤。
     */
    private RawDoc processItem(PolicyDataSource ds, HtmlCrawlConfig config,
                                ExtractedItem listItem, FetchContext ctx) {
        RawDoc raw = rawDocMapper.fromListItem(ds, listItem);
        if (raw == null) {
            return null;
        }

        // 前置增量过滤：避免为旧文档拉详情页
        if (shouldSkipByDate(raw, ctx)) {
            return null;
        }

        // 详情页补充
        if (config.hasDetail() && !isBlank(raw.getUrl())) {
            enrichDetail(raw, config);
        }

        // 兜底：标题和 URL 都为空时丢弃
        if (isBlank(raw.getTitle()) && isBlank(raw.getUrl())) {
            log.debug("HTML 源 {} 跳过空 item", ds.getName());
            return null;
        }

        // 详情页补充后可能更新了 publishDate，再次过滤
        if (shouldSkipByDate(raw, ctx)) {
            return null;
        }

        return raw;
    }

    // ──────────────────────── 详情页补充 ────────────────────────

    /**
     * 请求详情页，提取字段并合并到 RawDoc。
     */
    private void enrichDetail(RawDoc raw, HtmlCrawlConfig config) {
        Map<String, String> headers = parseHeaders(config.getDetailPage().getHeaders());
        HtmlPageResponse page = requestClient.fetch(raw.getUrl(), headers,
                config.getDetailTimeoutMs());
        if (!page.isSuccess()) {
            log.debug("详情页请求失败: url={} status={}", raw.getUrl(), page.getStatusCode());
            return;
        }

        List<PolicyExtractRule> detailRules = config.getDetailRules();
        if (detailRules.isEmpty()) {
            return;
        }

        ExtractedItem detail = extractionEngine.extractDetail(
                page.getHtml(), detailRules, raw.getUrl());

        rawDocMapper.mergeDetail(raw, detail);
    }

    // ──────────────────────── 分页 URL 构建 ────────────────────────

    /**
     * 根据分页规则构建所有列表页 URL。
     */
    private List<String> buildListUrls(HtmlCrawlConfig config) {
        String baseUrl = config.getListPage().getUrl();
        PolicyPaginationRule rule = config.getPaginationRule();
        if (rule == null || isBlank(rule.getMode()) || "NONE".equalsIgnoreCase(rule.getMode())) {
            List<String> urls = new ArrayList<>();
            urls.add(baseUrl);
            return urls;
        }

        if ("URL_TEMPLATE".equalsIgnoreCase(rule.getMode()) && !isBlank(rule.getUrlTemplate())) {
            int start = rule.getStartPage() == null ? 1 : rule.getStartPage();
            int max = rule.getMaxPages() == null ? 1 : Math.max(rule.getMaxPages(), 1);
            List<String> urls = new ArrayList<>();
            for (int page = start; page < start + max; page++) {
                urls.add(rule.getUrlTemplate().replace("{page}", String.valueOf(page)));
            }
            return urls;
        }

        log.warn("暂不支持分页模式 {}，仅抓取第一页", rule.getMode());
        List<String> urls = new ArrayList<>();
        urls.add(baseUrl);
        return urls;
    }

    // ──────────────────────── 请求头解析 ────────────────────────

    /**
     * 从 JSON 字符串解析请求头。
     * 解析失败时记日志并返回空 Map，不阻断抓取。
     */
    private Map<String, String> parseHeaders(String headersJson) {
        if (isBlank(headersJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(headersJson.trim(),
                    new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("解析请求头 JSON 失败，将使用空 headers: {}", headersJson, e);
            return Collections.emptyMap();
        }
    }

    // ──────────────────────── 增量过滤 ────────────────────────

    /**
     * 根据发布日期判断是否跳过该文档。
     */
    private boolean shouldSkipByDate(RawDoc rawDoc, FetchContext context) {
        return context.getLastPublishedAt() != null
                && rawDoc.getPublishDate() != null
                && rawDoc.getPublishDate().isBefore(context.getLastPublishedAt().toLocalDate());
    }

    // ──────────────────────── CompletableFuture 安全汇合 ────────────────────────

    private List<RawDoc> safeJoin(CompletableFuture<List<RawDoc>> future) {
        try {
            List<RawDoc> result = future.join();
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.error("处理列表页失败", e);
            return Collections.emptyList();
        }
    }

    private RawDoc safeJoinItem(CompletableFuture<RawDoc> future) {
        try {
            return future.join();
        } catch (Exception e) {
            log.error("处理 item 失败", e);
            return null;
        }
    }

    // ──────────────────────── 工具方法 ────────────────────────

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}