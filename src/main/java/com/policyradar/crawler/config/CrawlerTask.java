package com.policyradar.crawler.config;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * 爬虫任务包装类
 * 封装任务的标识、成功回调、失败回调
 */
public class CrawlerTask<T> {

    private final String taskId;

    private final Callable<T> callable;

    private final Consumer<T> onSuccess;

    private final Consumer<Throwable> onFailure;

    public CrawlerTask(String taskId, Callable<T> callable) {
        this(taskId, callable, null, null);
    }

    public CrawlerTask(String taskId, Callable<T> callable, Consumer<T> onSuccess) {
        this(taskId, callable, onSuccess, null);
    }

    public CrawlerTask(String taskId, Callable<T> callable, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        this.taskId = taskId;
        this.callable = callable;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    public String getTaskId() {
        return taskId;
    }

    public Callable<T> getCallable() {
        return callable;
    }

    public void onSuccess(T result) {
        if (onSuccess != null) {
            onSuccess.accept(result);
        }
    }

    public void onFailure(Throwable throwable) {
        if (onFailure != null) {
            onFailure.accept(throwable);
        }
    }
}