package com.policyradar.persistence;

/**
 * 流程执行结果
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor(force = true)
@lombok.AllArgsConstructor
public class PipelineResult {
    private final Long dataSourceId;
    private PipelineResultStatus status;
    private int fetchedCount;
    private int processedCount;
    private int uniqueCount;
    private int matchedCount;
    private int errorCount;
    private String error;
    private long durationMs;
}
