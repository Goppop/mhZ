package com.policyradar.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.policyradar.persistence.entity.PolicyCrawlTaskLog;
import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.mapper.PolicyCrawlTaskLogMapper;
import com.policyradar.persistence.mapper.PolicyDataSourceMapper;
import com.policyradar.pipeline.IngestPipeline;
import com.policyradar.persistence.PipelineResult;
import com.policyradar.sources.FetchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 数据源调度器
 *
 * 启动时从 policy_data_source 扫描所有 enabled=1 的数据源，
 * 按各自的 cron_expr 动态注册定时任务，
 * 也可通过 runNow() 手动立即触发某个数据源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceScheduler {

    private final PolicyDataSourceMapper dataSourceMapper;
    private final PolicyCrawlTaskLogMapper taskLogMapper;
    private final IngestPipeline pipeline;
    private final TaskScheduler taskScheduler;

    /** 已注册的定时任务，key = dataSource.id */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<PolicyDataSource> sources = loadEnabled();
        log.info("[Scheduler] 加载到 {} 个启用的数据源", sources.size());
        for (PolicyDataSource ds : sources) {
            register(ds);
        }
    }

    /**
     * 每 5 分钟重新扫描一次 DB，动态感知新增/变更的数据源
     */
    @Scheduled(fixedDelay = 300_000)
    public void refresh() {
        List<PolicyDataSource> sources = loadEnabled();
        for (PolicyDataSource ds : sources) {
            if (!scheduledTasks.containsKey(ds.getId())) {
                log.info("[Scheduler] 发现新数据源: {} (id={})", ds.getName(), ds.getId());
                register(ds);
            }
        }
    }

    /**
     * 立即执行指定数据源（手动触发，不等 Cron）
     */
    public PipelineResult runNow(Long dataSourceId) {
        PolicyDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new IllegalArgumentException("数据源不存在: " + dataSourceId);
        log.info("[Scheduler] 手动触发: {} (id={})", ds.getName(), ds.getId());
        return execute(ds);
    }

    /**
     * 立即执行所有启用的数据源
     */
    public List<PipelineResult> runAll() {
        return loadEnabled().stream()
                .map(this::execute)
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────────


    private void register(PolicyDataSource ds) {
        if (ds.getCronExpr() == null || ds.getCronExpr().isBlank()) {
            log.warn("[Scheduler] 数据源 {} 没有 cron_expr，跳过自动调度（仍可手动触发）", ds.getName());
            return;
        }
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> execute(ds),
                    new CronTrigger(ds.getCronExpr())
            );
            scheduledTasks.put(ds.getId(), future);
            log.info("[Scheduler] 已注册: {} → {}", ds.getName(), ds.getCronExpr());
        } catch (Exception e) {
            log.error("[Scheduler] 注册失败: {} - {}", ds.getName(), e.getMessage());
        }
    }

    private PipelineResult execute(PolicyDataSource ds) {
        PolicyCrawlTaskLog taskLog = startLog(ds);
        try {
            FetchContext ctx = FetchContext.builder()
                    .taskLogId(taskLog.getId())
                    .lastPublishedAt(ds.getLastPublishedAt())
                    .timeoutSec(30)
                    .debugMode(false)
                    .build();

            PipelineResult result = pipeline.ingest(ds, ctx);

            // 更新任务日志
            taskLog.setStatus(result.getStatus().name());
            taskLog.setFetchedCount(result.getFetchedCount());
            taskLog.setEndedAt(LocalDateTime.now());
            taskLogMapper.updateById(taskLog);

            // 更新增量游标
            if (result.getFetchedCount() > 0) {
                ds.setLastPublishedAt(LocalDateTime.now());
                dataSourceMapper.updateById(ds);
            }

            log.info("[Scheduler] 完成: {} → 抓取={} 入库={} 匹配={}",
                    ds.getName(), result.getFetchedCount(),
                    result.getUniqueCount(), result.getMatchedCount());
            return result;

        } catch (Exception e) {
            log.error("[Scheduler] 执行失败: {}", ds.getName(), e);
            taskLog.setStatus("FAILED");
            taskLog.setErrorMsg(e.getMessage());
            taskLog.setEndedAt(LocalDateTime.now());
            taskLogMapper.updateById(taskLog);
            throw new RuntimeException(e);
        }
    }

    private PolicyCrawlTaskLog startLog(PolicyDataSource ds) {
        PolicyCrawlTaskLog log = new PolicyCrawlTaskLog();
        log.setDataSourceId(ds.getId());
        log.setStatus("RUNNING");
        log.setFetchedCount(0);
        log.setStartedAt(LocalDateTime.now());
        taskLogMapper.insert(log);
        return log;
    }

    private List<PolicyDataSource> loadEnabled() {
        return dataSourceMapper.selectList(
                new LambdaQueryWrapper<PolicyDataSource>().eq(PolicyDataSource::getEnabled, true)
        );
    }
}
