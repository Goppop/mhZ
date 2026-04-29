package com.policyradar.crawler.example;

import com.policyradar.crawler.config.CrawlerTask;
import com.policyradar.crawler.service.CrawlerThreadPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * 使用示例：展示如何直接使用线程池提交多任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerTaskExample {

    private final CrawlerThreadPoolService crawlerThreadPoolService;

    /**
     * 示例1：使用 CrawlerTask 包装任务，支持成功/失败回调
     */
    public void submitCrawlerTaskExample() {
        List<String> urls = List.of(
                "https://www.example1.com",
                "https://www.example2.com",
                "https://www.example3.com"
        );

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String taskId = "crawl-task-" + i;

            CrawlerTask<String> task = new CrawlerTask<>(
                    taskId,
                    // 任务逻辑
                    () -> {
                        log.info("正在爬取: {}", url);
                        // 这里是你的爬取逻辑
                        Thread.sleep(1000); // 模拟耗时操作
                        return "爬取完成: " + url;
                    },
                    // 成功回调
                    result -> log.info("{}", result),
                    // 失败回调
                    error -> log.error("任务失败: {}", taskId, error)
            );

            crawlerThreadPoolService.submit(task);
        }
    }

    /**
     * 示例2：使用 submit 返回 Future
     */
    public List<Future<String>> submitFutureExample() {
        List<Future<String>> futures = new ArrayList<>();

        List<String> urls = List.of(
                "https://www.example1.com",
                "https://www.example2.com",
                "https://www.example3.com"
        );

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            Future<String> future = crawlerThreadPoolService.submit(
                    "crawl-future-" + i,
                    () -> {
                        log.info("爬取中: {}", url);
                        Thread.sleep(500);
                        return "成功: " + url;
                    }
            );
            futures.add(future);
        }

        return futures;
    }

    /**
     * 示例3：使用 execute 方式提交 Runnable
     */
    public void executeExample() {
        List<String> urls = List.of(
                "https://www.example1.com",
                "https://www.example2.com",
                "https://www.example3.com"
        );

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String taskId = "crawl-exec-" + i;

            crawlerThreadPoolService.execute(taskId, () -> {
                log.info("执行任务: {}", taskId);
                try {
                    Thread.sleep(500);
                    // 执行你的爬取逻辑
                    log.info("任务完成: {}", taskId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("任务中断", e);
                }
            });
        }
    }

    /**
     * 示例4：结合 CompletableFuture 和自定义线程池
     */
    public void completableFutureExample() {
        List<String> urls = List.of(
                "https://www.example1.com",
                "https://www.example2.com",
                "https://www.example3.com"
        );

        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (String url : urls) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                log.info("爬取: {}", url);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Result from " + url;
            }, crawlerThreadPoolService);

            futures.add(future);
        }

        // 等待所有任务完成并收集结果
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("所有任务完成！");
                    for (CompletableFuture<String> future : futures) {
                        try {
                            log.info("结果: {}", future.get());
                        } catch (Exception e) {
                            log.error("获取结果失败", e);
                        }
                    }
                });
    }
}