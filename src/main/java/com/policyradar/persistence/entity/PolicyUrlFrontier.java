package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 候选 URL 队列实体
 *
 * 存储由搜索 / 列表爬取发现的候选 URL，
 * 由 FrontierConsumer 消费并抓取详情页，最终写入 policy_document。
 */
@Data
@TableName("policy_url_frontier")
public class PolicyUrlFrontier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String url;

    /** SHA1(url)，UNIQUE，防重复入队 */
    private String urlHash;

    /** 来自哪个 type=SEARCH 的 policy_data_source */
    private Long dataSourceId;

    /** 来自哪条 policy_keyword */
    private Long keywordId;

    /** 检索器名称，如 gov_cn */
    private String provider;

    /** 搜索结果片段（标题），便于调试 */
    private String titleSnippet;

    /**
     * 状态：PENDING / FETCHING / FETCHED / FAILED / SKIPPED
     */
    private String status;

    private Integer retryCount;

    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime discoveredAt;

    private LocalDateTime fetchedAt;
}
