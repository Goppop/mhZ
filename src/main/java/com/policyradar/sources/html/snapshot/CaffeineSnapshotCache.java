package com.policyradar.sources.html.snapshot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CaffeineSnapshotCache implements HtmlSnapshotCache {

    private final Cache<String, HtmlSnapshot> cache;

    public CaffeineSnapshotCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .removalListener((String key, HtmlSnapshot value, RemovalCause cause) -> {
                    log.debug("[SnapshotCache] 逐出 snapshotId={} cause={}", key, cause);
                })
                .build();
    }

    @Override
    public String put(HtmlSnapshot snapshot) {
        String id = UUID.randomUUID().toString();
        snapshot.setSnapshotId(id);
        cache.put(id, snapshot);
        log.info("[SnapshotCache] put snapshotId={} url={} htmlLength={}", id, snapshot.getUrl(), snapshot.getHtml().length());
        return id;
    }

    @Override
    public HtmlSnapshot get(String snapshotId) {
        HtmlSnapshot s = cache.getIfPresent(snapshotId);
        if (s != null) {
            log.debug("[SnapshotCache] hit snapshotId={}", snapshotId);
        } else {
            log.warn("[SnapshotCache] miss snapshotId={}", snapshotId);
        }
        return s;
    }

    @Override
    public void evict(String snapshotId) {
        cache.invalidate(snapshotId);
    }
}
