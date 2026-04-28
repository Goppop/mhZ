package com.policyradar.pipeline;

import com.policyradar.persistence.PipelineResult;
import com.policyradar.persistence.PipelineResultStatus;
import com.policyradar.persistence.entity.PolicyDocument;
import com.policyradar.persistence.mapper.PolicyDocumentMapper;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.RunnerRegistry;
import com.policyradar.sources.SourceRunner;
import com.policyradar.persistence.entity.PolicyDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据采集主流水线
 *
 * 串联整个数据处理流：
 * source fetch → raw payload storage → normalization → deduplication → match → save
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestPipeline {

    private final RunnerRegistry runnerRegistry;
    private final RawPayloadWriter rawPayloadWriter;
    private final Normalizer normalizer;
    private final Deduper deduplicator;
    private final KeywordMatcher keywordMatcher;
    private final PolicyDocumentMapper documentMapper;

    /**
     * 直接处理已抓取的 RawDoc 列表（供 FrontierConsumer 调用，跳过 Runner fetch 步骤）
     *
     * @param rawDocs    已抓取的原始文档列表
     * @param dataSource 来源数据源（用于记录和上下文）
     * @param context    抓取上下文
     * @return 流程执行结果
     */
    public PipelineResult ingestRawDocs(List<RawDoc> rawDocs, PolicyDataSource dataSource, FetchContext context) {
        log.info("直接处理 {} 条 RawDoc，来源: {}", rawDocs.size(), dataSource.getName());
        long startTime = System.currentTimeMillis();
        PipelineResult result = PipelineResult.builder()
                .dataSourceId(dataSource.getId())
                .status(PipelineResultStatus.SUCCESS)
                .fetchedCount(rawDocs.size())
                .processedCount(0)
                .uniqueCount(0)
                .matchedCount(0)
                .errorCount(0)
                .build();

        try {
            if (rawDocs.isEmpty()) {
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }
            rawPayloadWriter.writeRawPayload(context.getTaskLogId(), dataSource.getId(), rawDocs);

            List<com.policyradar.persistence.entity.PolicyDocument> normalizedDocs = normalizer.normalize(rawDocs);
            result.setProcessedCount(normalizedDocs.size());
            if (normalizedDocs.isEmpty()) {
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            List<com.policyradar.persistence.entity.PolicyDocument> uniqueDocs = deduplicator.deduplicate(normalizedDocs);
            result.setUniqueCount(uniqueDocs.size());

            int matched = 0;
            for (com.policyradar.persistence.entity.PolicyDocument doc : uniqueDocs) {
                List<KeywordMatcher.MatchResult> matches = keywordMatcher.match(doc);
                if (!matches.isEmpty()) matched++;
                try { documentMapper.insert(doc); }
                catch (Exception e) {
                    log.error("文档保存失败: {} - {}", doc.getUrl(), e.getMessage());
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
            result.setMatchedCount(matched);
            result.setStatus(PipelineResultStatus.SUCCESS);
        } catch (Exception e) {
            log.error("ingestRawDocs 失败", e);
            result.setStatus(PipelineResultStatus.FAILED);
            result.setError(e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 执行完整的采集流程
     *
     * @param dataSource 数据源配置
     * @param context 任务日志 ID
     * @return 流程执行结果
     */
    public PipelineResult ingest(PolicyDataSource dataSource, FetchContext context) {
        log.info("开始执行采集流程: {}", dataSource.getName());

        long startTime = System.currentTimeMillis();
        PipelineResult result = PipelineResult.builder()
                .dataSourceId(dataSource.getId())
                .status(PipelineResultStatus.SUCCESS) // 默认成功
                .fetchedCount(0)
                .processedCount(0)
                .uniqueCount(0)
                .matchedCount(0)
                .errorCount(0)
                .build();

        try {
            // Step 1: 获取对应的 SourceRunner
            SourceRunner runner = runnerRegistry.getRunner(dataSource.getType());
            if (runner == null) {
                log.error("不支持的数据源类型: {}", dataSource.getType());
                result.setStatus(PipelineResultStatus.FAILED);
                result.setError("不支持的数据源类型");
                return result;
            }

            // Step 2: 调用 Runner 抓取数据
            log.info("正在抓取数据，type: {}", dataSource.getType());
            List<RawDoc> rawDocs = runner.fetch(dataSource, context);
            result.setFetchedCount(rawDocs.size());
            log.info("数据抓取完成，获取 {} 条原始文档", rawDocs.size());

            if (rawDocs.isEmpty()) {
                result.setStatus(PipelineResultStatus.SUCCESS);
                result.setProcessedCount(0);
                log.debug("没有获取到新数据，跳过后续处理");
                return result;
            }

            // Step 3: 保存原始内容
            rawPayloadWriter.writeRawPayload(context.getTaskLogId(), dataSource.getId(), rawDocs);
            log.debug("原始内容保存完成");

            // Step 4: 标准化处理
            log.debug("正在进行标准化处理...");
            List<PolicyDocument> normalizedDocs = normalizer.normalize(rawDocs);
            result.setProcessedCount(normalizedDocs.size());
            log.debug("标准化完成，有效文档 {} 条", normalizedDocs.size());

            if (normalizedDocs.isEmpty()) {
                result.setStatus(PipelineResultStatus.SUCCESS);
                log.debug("没有有效的标准化文档，跳过后续处理");
                return result;
            }

            // Step 5: 去重
            log.debug("正在进行去重...");
            List<PolicyDocument> uniqueDocs = deduplicator.deduplicate(normalizedDocs);
            result.setUniqueCount(uniqueDocs.size());
            log.debug("去重完成，保留 {} 条唯一文档", uniqueDocs.size());

            if (uniqueDocs.isEmpty()) {
                result.setStatus(PipelineResultStatus.SUCCESS);
                log.debug("所有文档都已重复，跳过后续处理");
                return result;
            }

            // Step 6: 关键词匹配（可选）
            if (context.isDebugMode()) {
                // 匹配关键词
                int matchedCount = 0;
                for (PolicyDocument doc : uniqueDocs) {
                    List<KeywordMatcher.MatchResult> matches = keywordMatcher.match(doc);
                    if (!matches.isEmpty()) {
                        matchedCount++;
                        log.debug("文档匹配 {} 个关键词: {}", doc.getTitle(), matches.size());
                    }
                }
                result.setMatchedCount(matchedCount);
                log.debug("关键词匹配完成，{} 条文档匹配到关键词", matchedCount);
            }

            // Step 7: 保存到数据库
            log.debug("正在保存到数据库...");
            for (PolicyDocument doc : uniqueDocs) {
                try {
                    documentMapper.insert(doc);
                } catch (Exception e) {
                    log.error("文档保存失败: {} - {}", doc.getUrl(), e.getMessage(), e);
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }

            // 设置成功状态
            result.setStatus(PipelineResultStatus.SUCCESS);
            log.info("采集流程完成，数据已保存到数据库");

        } catch (Exception e) {
            log.error("采集流程执行失败", e);
            result.setStatus(PipelineResultStatus.FAILED);
            result.setError(e.getMessage());
            result.setErrorCount(1);
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        log.info("采集流程结束，耗时 {}ms", result.getDurationMs());

        return result;
    }

}