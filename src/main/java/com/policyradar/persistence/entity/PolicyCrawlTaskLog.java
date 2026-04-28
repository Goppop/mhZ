package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 抓取任务日志表实体类
 *
 * 存储政策抓取任务的执行日志
 */
@Data
@TableName("policy_crawl_task_log")
public class PolicyCrawlTaskLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联数据源ID
     */
    private Long dataSourceId;

    /**
     * 状态：RUNNING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 抓取条数
     */
    private Integer fetchedCount;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime endedAt;
}