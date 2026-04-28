package com.policyradar.frontier;

import com.policyradar.infra.CrawlProperties;
import com.policyradar.infra.DomainGuard;
import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.entity.PolicyUrlFrontier;
import com.policyradar.persistence.mapper.PolicyUrlFrontierMapper;
import com.policyradar.pipeline.IngestPipeline;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 候选 URL 队列消费者
 *
 * 定时从 policy_url_frontier 拉取 PENDING 状态的 URL，
 * 抓取详情页 → DetailRouter 解析 → IngestPipeline 写入。
 *
 * <p>状态机：PENDING → FETCHING → FETCHED / FAILED(重试) / SKIPPED</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrontierConsumer {

    private final PolicyUrlFrontierMapper frontierMapper;
    private final DomainGuard domainGuard;
    private final DetailRouter detailRouter;
    private final IngestPipeline pipeline;
    private final CrawlProperties props;

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private int uaIndex = 0;

    /**
     * 每 30 秒消费一批候选 URL
     */
    @Scheduled(fixedDelay = 30_000)
    public void consume() {
        int batchSize = props.getFrontierBatchSize();
        List<PolicyUrlFrontier> batch = frontierMapper.selectPending(batchSize);
        if (batch.isEmpty()) return;

        log.info("[FrontierConsumer] 拉取 {} 条待抓取候选 URL", batch.size());
        for (PolicyUrlFrontier item : batch) {
            processOne(item);
        }
    }

    private void processOne(PolicyUrlFrontier item) {
        // 乐观锁：只有成功将 PENDING→FETCHING 才继续处理（防并发重复）
        int updated = frontierMapper.markFetching(item.getId());
        if (updated == 0) {
            log.debug("[FrontierConsumer] 已被其他线程领取，跳过: {}", item.getUrl());
            return;
        }

        // 白名单校验
        if (!domainGuard.allow(item.getUrl())) {
            frontierMapper.markSkipped(item.getId(), "domain not allowed");
            log.info("[FrontierConsumer] 域名不在白名单，跳过: {}", item.getUrl());
            return;
        }

        try {
            domainGuard.acquireSlot(item.getUrl());
            String ua = pickUserAgent();
            String html = Jsoup.connect(item.getUrl())
                    .userAgent(ua)
                    .timeout(props.getFetchTimeoutMs())
                    .ignoreHttpErrors(true)
                    .get()
                    .html();

            RawDoc doc = detailRouter.parse(item.getUrl(), html);

            // 补充 titleSnippet 作为 title 兜底
            if ((doc.getTitle() == null || doc.getTitle().isBlank())
                    && item.getTitleSnippet() != null) {
                doc.setTitle(item.getTitleSnippet());
            }

            // 送入流水线（使用来源 dataSource 做上下文）
            PolicyDataSource ds = buildFakeDsForPipeline(item);
            FetchContext ctx = FetchContext.builder()
                    .taskLogId(null)
                    .debugMode(false)
                    .build();
            pipeline.ingestRawDocs(List.of(doc), ds, ctx);

            frontierMapper.markFetched(item.getId());
            log.info("[FrontierConsumer] 完成: {}", item.getUrl());

        } catch (Exception e) {
            log.warn("[FrontierConsumer] 抓取失败 {}: {}", item.getUrl(), e.getMessage());
            frontierMapper.markFailedOrRetry(item.getId(), e.getMessage(), props.getMaxRetry());
        }
    }

    /** 从配置池轮换 UA，避免被特征识别 */
    private synchronized String pickUserAgent() {
        List<String> agents = props.getUserAgents();
        if (agents.isEmpty()) return DEFAULT_UA;
        String ua = agents.get(uaIndex % agents.size());
        uaIndex++;
        return ua;
    }

    /**
     * 构造一个虚拟的 PolicyDataSource 用于传入 IngestPipeline，
     * 使流水线可以记录 dataSourceId。
     */
    private PolicyDataSource buildFakeDsForPipeline(PolicyUrlFrontier item) {
        PolicyDataSource ds = new PolicyDataSource();
        ds.setId(item.getDataSourceId());
        ds.setName("frontier:" + item.getProvider());
        ds.setType("SEARCH");
        return ds;
    }

    // ── 扩展钩子：PolicyUrlFrontier 需要 hintedPublishDate 字段供此处使用 ──
    // 当前版本通过 GenericExtractor 自动抽取日期，此处不需要额外操作
}
