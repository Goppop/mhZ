package com.policyradar.crawler.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池监控接口
 */
@Slf4j
@RestController
@RequestMapping("/api/crawler/threadpool")
public class ThreadPoolMonitorController {

    private final ThreadPoolTaskExecutor executor;

    public ThreadPoolMonitorController(@Qualifier("crawlerThreadPool") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        ThreadPoolExecutor tp = executor.getThreadPoolExecutor();
        return Map.of(
                "ok", true,
                "corePoolSize", tp.getCorePoolSize(),
                "maxPoolSize", tp.getMaximumPoolSize(),
                "poolSize", tp.getPoolSize(),
                "activeCount", tp.getActiveCount(),
                "queueSize", tp.getQueue().size(),
                "taskCount", tp.getTaskCount(),
                "completedTaskCount", tp.getCompletedTaskCount()
        );
    }
}
