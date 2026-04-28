package com.policyradar.sources;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抓取上下文
 *
 * 包含抓取过程中需要的所有上下文信息，如增量抓取游标、任务日志ID等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchContext {

    /**
     * 任务日志ID
     */
    private Long taskLogId;

    /**
     * 增量抓取游标：上次抓取的最大发布日期
     */
    private LocalDateTime lastPublishedAt;

    /**
     * 抓取超时时间（秒
     */
    private int timeoutSec;

    /**
     * 是否启用调试模式
     */
    private boolean debugMode;
}