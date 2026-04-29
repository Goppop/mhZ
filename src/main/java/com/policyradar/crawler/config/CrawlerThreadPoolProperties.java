package com.policyradar.crawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 爬虫线程池配置属性类
 * 支持在 application.yml/properties 中配置线程池参数
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "policy-radar.crawler.thread-pool")
public class CrawlerThreadPoolProperties {

    /**
     * 核心线程数，默认 8
     */
    private int corePoolSize = 8;

    /**
     * 最大线程数，默认 20
     */
    private int maximumPoolSize = 20;

    /**
     * 线程保持活动时间（秒），默认 60 秒
     */
    private long keepAliveTime = 60;

    /**
     * 任务队列容量，默认 100
     */
    private int queueCapacity = 100;

    /**
     * 线程池名称前缀，用于识别日志和监控
     */
    private String threadNamePrefix = "crawler-pool-";

    /**
     * 是否允许核心线程超时，默认 false
     */
    private boolean allowCoreThreadTimeOut = false;
}