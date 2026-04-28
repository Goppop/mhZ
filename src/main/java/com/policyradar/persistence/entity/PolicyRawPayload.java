package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 原始内容存储表实体类
 *
 * 存储每次抓取的原始内容，便于故障排查和数据回放
 */
@Data
@TableName("policy_raw_payload")
public class PolicyRawPayload {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联抓取任务日志ID
     */
    private Long taskLogId;

    /**
     * 关联数据源ID
     */
    private Long dataSourceId;

    /**
     * 内容类型：JSON/HTML/RSS_XML
     */
    private String payloadType;

    /**
     * 原始内容（太大时可考虑文件系统存储）
     */
    private String payload;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}