package com.policyradar.crawler.service;

import lombok.Builder;
import lombok.Data;

/**
 * 线程池状态信息
 */
@Data
@Builder
public class ThreadPoolStatus {

    /**
     * 当前活动线程数
     */
    private int activeCount;

    /**
     * 核心线程数
     */
    private int corePoolSize;

    /**
     * 最大线程数
     */
    private int maximumPoolSize;

    /**
     * 线程池当前大小
     */
    private int poolSize;

    /**
     * 已完成任务数
     */
    private long completedTaskCount;

    /**
     * 总任务数
     */
    private long taskCount;

    /**
     * 队列中等待的任务数
     */
    private int queueSize;

    /**
     * 队列剩余容量
     */
    private int remainingCapacity;
}