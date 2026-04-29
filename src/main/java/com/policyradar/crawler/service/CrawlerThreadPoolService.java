package com.policyradar.crawler.service;

import com.policyradar.crawler.config.CrawlerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * 爬虫线程池服务类
 * 提供任务提交、管理和监控功能
 */
@Slf4j
@Service
public class CrawlerThreadPoolService implements Executor {

    private final ExecutorService crawlerExecutor;

    private final ThreadPoolExecutor threadPoolExecutor;

    private final ConcurrentHashMap<String, Future<?>> taskFutureMap = new ConcurrentHashMap<>();

    public CrawlerThreadPoolService(@Qualifier("crawlerThreadPool") Executor executor) {
        // 转换为 ThreadPoolExecutor 以便进行更详细的监控
        if (executor instanceof ThreadPoolExecutor threadPool) {
            this.threadPoolExecutor = threadPool;
            this.crawlerExecutor = threadPool;
        } else {
            throw new IllegalArgumentException("Executor must be an instance of ThreadPoolExecutor");
        }
    }

    @Override
    public void execute(Runnable command) {
        crawlerExecutor.execute(command);
    }

    /**
     * 提交任务（带任务ID）
     */
    public <T> void submit(CrawlerTask<T> task) {
        Objects.requireNonNull(task, "Task cannot be null");
        Objects.requireNonNull(task.getTaskId(), "Task ID cannot be null");

        Future<T> future = crawlerExecutor.submit(() -> {
            try {
                T result = task.getCallable().call();
                task.onSuccess(result);
                return result;
            } catch (Throwable e) {
                task.onFailure(e);
                log.error("任务执行失败: {}", task.getTaskId(), e);
                throw e;
            }
        });

        taskFutureMap.put(task.getTaskId(), future);
    }

    /**
     * 提交任务并返回 Future
     */
    public <T> Future<T> submit(String taskId, Callable<T> callable) {
        CrawlerTask<T> task = new CrawlerTask<>(taskId, callable);
        Future<T> future = crawlerExecutor.submit(() -> {
            try {
                return callable.call();
            } catch (Throwable e) {
                log.error("任务执行失败: {}", taskId, e);
                throw e;
            }
        });
        taskFutureMap.put(taskId, future);
        return future;
    }

    /**
     * 提交任务（Runnable形式）
     */
    public void execute(String taskId, Runnable runnable) {
        crawlerExecutor.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                log.error("任务执行失败: {}", taskId, e);
            }
        });
    }

    /**
     * 取消任务
     */
    public boolean cancel(String taskId) {
        Future<?> future = taskFutureMap.get(taskId);
        if (future != null && !future.isDone() && !future.isCancelled()) {
            boolean canceled = future.cancel(true);
            if (canceled) {
                taskFutureMap.remove(taskId);
                log.debug("任务取消成功: {}", taskId);
            }
            return canceled;
        }
        return false;
    }

    /**
     * 获取任务状态
     */
    public String getTaskStatus(String taskId) {
        Future<?> future = taskFutureMap.get(taskId);
        if (future == null) {
            return "UNKNOWN";
        }
        if (future.isCancelled()) {
            return "CANCELED";
        }
        if (future.isDone()) {
            return "DONE";
        }
        return "RUNNING";
    }

    /**
     * 等待任务完成
     */
    public <T> T await(String taskId, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Future<?> future = taskFutureMap.get(taskId);
        if (future == null) {
            throw new IllegalStateException("Task not found: " + taskId);
        }
        try {
            @SuppressWarnings("unchecked")
            T result = (T) future.get(timeout, unit);
            return result;
        } finally {
            taskFutureMap.remove(taskId);
        }
    }

    /**
     * 获取线程池状态信息
     */
    public ThreadPoolStatus getThreadPoolStatus() {
        return ThreadPoolStatus.builder()
                .activeCount(threadPoolExecutor.getActiveCount())
                .corePoolSize(threadPoolExecutor.getCorePoolSize())
                .maximumPoolSize(threadPoolExecutor.getMaximumPoolSize())
                .poolSize(threadPoolExecutor.getPoolSize())
                .taskCount(threadPoolExecutor.getTaskCount())
                .completedTaskCount(threadPoolExecutor.getCompletedTaskCount())
                .queueSize(threadPoolExecutor.getQueue().size())
                .remainingCapacity(threadPoolExecutor.getQueue().remainingCapacity())
                .build();
    }

    /**
     * 关闭线程池（建议仅在应用关闭时调用）
     */
    public void shutdown() {
        log.info("正在关闭爬虫线程池...");
        crawlerExecutor.shutdown();
        try {
            if (!crawlerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("线程池未在规定时间内关闭，强制关闭");
                crawlerExecutor.shutdownNow();
                if (!crawlerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("线程池强制关闭失败");
                }
            }
        } catch (InterruptedException e) {
            crawlerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        taskFutureMap.clear();
        log.info("爬虫线程池已关闭");
    }
}