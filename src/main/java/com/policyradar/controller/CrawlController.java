package com.policyradar.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.policyradar.persistence.PipelineResult;
import com.policyradar.persistence.entity.PolicyCrawlTaskLog;
import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.persistence.entity.PolicyDocument;
import com.policyradar.persistence.entity.PolicyUrlFrontier;
import com.policyradar.persistence.mapper.PolicyCrawlTaskLogMapper;
import com.policyradar.persistence.mapper.PolicyDataSourceMapper;
import com.policyradar.persistence.mapper.PolicyDocumentMapper;
import com.policyradar.persistence.mapper.PolicyUrlFrontierMapper;
import com.policyradar.scheduler.DataSourceScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 爬虫手动触发与状态查询接口
 *
 * 调试专用，方便不等 Cron 直接触发并观察结果。
 *
 * 接口列表（基础路径 /api/crawl）：
 *   GET  /sources               查看所有数据源
 *   POST /run/{id}              立即执行某个数据源
 *   POST /run-all               立即执行所有启用的数据源
 *   GET  /status                查看最近抓取日志
 *   GET  /frontier              查看候选 URL 队列
 *   GET  /documents             查看最近写入的文档
 */
@Slf4j
@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final DataSourceScheduler scheduler;
    private final PolicyDataSourceMapper dataSourceMapper;
    private final PolicyCrawlTaskLogMapper taskLogMapper;
    private final PolicyDocumentMapper documentMapper;
    private final PolicyUrlFrontierMapper frontierMapper;

    /**
     * 查看所有数据源（含状态）
     */
    @GetMapping("/sources")
    public ResponseEntity<?> listSources() {
        List<PolicyDataSource> sources = dataSourceMapper.selectList(null);
        return ok(sources);
    }

    /**
     * 立即执行某个数据源
     * 例：POST /api/crawl/run/1
     */
    @PostMapping("/run/{id}")
    public ResponseEntity<?> runOne(@PathVariable Long id) {
        log.info("[CrawlController] 手动触发数据源 id={}", id);
        try {
            PipelineResult result = scheduler.runNow(id);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", result.getStatus());
            resp.put("fetched", result.getFetchedCount());
            resp.put("unique", result.getUniqueCount());
            resp.put("matched", result.getMatchedCount());
            resp.put("errors", result.getErrorCount());
            resp.put("durationMs", result.getDurationMs());
            return ok(resp);
        } catch (Exception e) {
            return error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 立即执行所有启用的数据源
     */
    @PostMapping("/run-all")
    public ResponseEntity<?> runAll() {
        log.info("[CrawlController] 手动触发全部数据源");
        try {
            List<PipelineResult> results = scheduler.runAll();
            return ok(Map.of("count", results.size(), "results", results));
        } catch (Exception e) {
            return error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 查看最近 20 条任务日志
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        List<PolicyCrawlTaskLog> logs = taskLogMapper.selectList(
                new LambdaQueryWrapper<PolicyCrawlTaskLog>()
                        .orderByDesc(PolicyCrawlTaskLog::getStartedAt)
                        .last("LIMIT 20")
        );
        return ok(logs);
    }

    /**
     * 查看候选 URL 队列状态
     * 可选 ?status=PENDING/FETCHING/FETCHED/FAILED
     */
    @GetMapping("/frontier")
    public ResponseEntity<?> frontier(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<PolicyUrlFrontier> wrapper = new LambdaQueryWrapper<PolicyUrlFrontier>()
                .orderByDesc(PolicyUrlFrontier::getDiscoveredAt)
                .last("LIMIT 50");
        if (status != null && !status.isBlank()) {
            wrapper.eq(PolicyUrlFrontier::getStatus, status.toUpperCase());
        }
        List<PolicyUrlFrontier> items = frontierMapper.selectList(wrapper);

        // 加一个汇总统计
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", items);
        resp.put("total", items.size());
        return ok(resp);
    }

    /**
     * 查看最近写入的 policy_document
     * 可选 ?limit=20
     */
    @GetMapping("/documents")
    public ResponseEntity<?> documents(@RequestParam(defaultValue = "20") int limit) {
        List<PolicyDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<PolicyDocument>()
                        .orderByDesc(PolicyDocument::getCreatedAt)
                        .last("LIMIT " + Math.min(limit, 100))
        );
        return ok(Map.of("count", docs.size(), "docs", docs));
    }

    // ──────────────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Map<String, Object>> error(String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", msg);
        return ResponseEntity.status(500).body(resp);
    }
}
