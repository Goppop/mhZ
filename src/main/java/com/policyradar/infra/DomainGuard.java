package com.policyradar.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 出站域名守卫
 *
 * 所有 SearchProvider / FrontierConsumer 的出站 HTTP 请求必须先经过本组件：
 * 1. 白名单校验：域名不在 allowed_domains 后缀列表内 → 拒绝
 * 2. QPS 限速：每域一个令牌桶，防止过快请求被封
 *
 * 令牌桶用 Semaphore + 定时补充简单实现，避免引入 Guava RateLimiter 的依赖。
 * 如果 pom 中已有 Guava，可替换为 RateLimiter.create(qps).acquire()。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainGuard {

    private final CrawlProperties props;

    /** 每个域的"桶大小"（突发上限） */
    private static final int BURST = 3;

    /** 按域维护的 Semaphore（令牌桶） */
    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (props.getAllowedDomains().isEmpty()) {
            log.warn("[DomainGuard] allowed_domains 未配置，所有域名均被拒绝！请在 application.yml 中配置");
        } else {
            log.info("[DomainGuard] 白名单域名: {}", props.getAllowedDomains());
        }
    }

    /**
     * 判断 URL 是否在白名单内
     */
    public boolean allow(String url) {
        String host = extractHost(url);
        if (host == null) return false;
        List<String> allowed = props.getAllowedDomains();
        if (allowed.isEmpty()) return false;
        return allowed.stream().anyMatch(host::endsWith);
    }

    /**
     * 阻塞直到获得该域的访问令牌（QPS 控制）
     * 若 2 倍超时内无法获取令牌，抛出异常中断本次请求。
     */
    public void acquireSlot(String url) {
        String domain = extractHost(url);
        if (domain == null) return;
        Semaphore sem = semaphores.computeIfAbsent(domain, d -> new Semaphore(BURST));
        try {
            double qps = props.getQpsPerDomain().getOrDefault(domain, props.getDefaultQps());
            long waitMs = (long) (1000.0 / qps);
            boolean acquired = sem.tryAcquire(waitMs * 4, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RuntimeException("[DomainGuard] 域 " + domain + " 令牌等待超时，跳过本次请求");
            }
            // 异步归还令牌（模拟滑动窗口）
            long delay = waitMs;
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                sem.release();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[DomainGuard] acquireSlot 被中断", e);
        }
    }

    private String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            log.debug("[DomainGuard] 无法解析 URL 域名: {}", url);
            return null;
        }
    }
}
