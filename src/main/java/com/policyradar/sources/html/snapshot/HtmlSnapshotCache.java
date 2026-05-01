package com.policyradar.sources.html.snapshot;

public interface HtmlSnapshotCache {
    /**
     * 缓存快照，返回 snapshotId。
     */
    String put(HtmlSnapshot snapshot);

    /**
     * 取快照，不存在返回 null。
     */
    HtmlSnapshot get(String snapshotId);

    /**
     * 逐出快照。
     */
    void evict(String snapshotId);
}
