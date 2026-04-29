package com.policyradar.crawler.controller;

import com.policyradar.crawler.service.CrawlerThreadPoolService;
import com.policyradar.crawler.service.ThreadPoolStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 线程池监控接口
 */
@Slf4j
@RestController
@RequestMapping("/api/crawler/threadpool")
@RequiredArgsConstructor
public class ThreadPoolMonitorController {

    private final CrawlerThreadPoolService crawlerThreadPoolService;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        ThreadPoolStatus status = crawlerThreadPoolService.getThreadPoolStatus();
        return Map.of(
                "status", status,
                "ok", true
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        ThreadPoolStatus status = crawlerThreadPoolService.getThreadPoolStatus();
        return Map.of(
                "corePoolSize", status.getCorePoolSize(),
                "maxPoolSize", status.getMaximumPoolSize(),
                "poolSize", status.getPoolSize(),
                "activeCount", status.getActiveCount(),
                "queueSize", status.getQueueSize(),
                "taskCount", status.getTaskCount(),
                "completedTaskCount", status.getCompletedTaskCount(),
                "remainingCapacity", status.getRemainingCapacity()
        );
    }
}