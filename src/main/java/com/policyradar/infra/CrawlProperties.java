package com.policyradar.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 爬虫基础设施配置
 */
@Data
@ConfigurationProperties(prefix = "policy-radar.crawl")
public class CrawlProperties {

    /** 允许爬取的域名后缀白名单，如 .gov.cn */
    private List<String> allowedDomains = new ArrayList<>();

    /** 按域名的 QPS 限制；不命中时使用 defaultQps */
    private Map<String, Double> qpsPerDomain = new HashMap<>();

    /** 默认每域 QPS */
    private double defaultQps = 1.0;

    /** User-Agent 轮换池 */
    private List<String> userAgents = new ArrayList<>();

    /** 是否遵守 robots.txt（生产建议打开） */
    private boolean respectRobots = false;

    /** FrontierConsumer 每批拉取数量 */
    private int frontierBatchSize = 20;

    /** 最大重试次数 */
    private int maxRetry = 3;

    /** 详情页抓取超时（毫秒） */
    private int fetchTimeoutMs = 15_000;
}
