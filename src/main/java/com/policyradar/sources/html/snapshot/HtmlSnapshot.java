package com.policyradar.sources.html.snapshot;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class HtmlSnapshot {
    private String snapshotId;
    private String url;
    private String finalUrl;
    private int statusCode;
    private String html;
    private String title;
    private Instant fetchedAt;
}
