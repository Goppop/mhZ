package com.policyradar.crawler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 爬虫线程池配置
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CrawlerThreadPoolProperties.class)
public class CrawlerThreadPoolConfig {

    private final CrawlerThreadPoolProperties properties;

    public CrawlerThreadPoolConfig(CrawlerThreadPoolProperties properties) {
        this.properties = properties;
    }

    @Bean("crawlerThreadPool")
    public ThreadPoolTaskExecutor crawlerThreadPool() {
        log.info("爬虫线程池: core={}, max={}, queue={}, prefix={}",
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}