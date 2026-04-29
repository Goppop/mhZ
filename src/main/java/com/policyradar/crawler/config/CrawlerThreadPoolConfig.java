package com.policyradar.crawler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 爬虫线程池配置类
 * 创建和配置全局共享的线程池实例
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CrawlerThreadPoolProperties.class)
public class CrawlerThreadPoolConfig {

    private final CrawlerThreadPoolProperties properties;

    public CrawlerThreadPoolConfig(CrawlerThreadPoolProperties properties) {
        this.properties = properties;
    }

    /**
     * 爬虫专用线程池
     */
    @Bean("crawlerThreadPool")
    public Executor crawlerThreadPool() {
        log.info("配置爬虫线程池: 核心线程数={}, 最大线程数={}, 队列容量={}, 线程前缀={}",
                properties.getCorePoolSize(),
                properties.getMaximumPoolSize(),
                properties.getQueueCapacity(),
                properties.getThreadNamePrefix());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaximumPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setKeepAliveSeconds((int) properties.getKeepAliveTime());
        executor.setAllowCoreThreadTimeOut(properties.isAllowCoreThreadTimeOut());

        // 拒绝策略：当队列满了且线程数已达到最大值时，由调用线程处理任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化线程池
        executor.initialize();

        return executor;
    }
}