package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据源配置表实体类
 *
 * 存储系统支持的政策数据源配置信息
 */
@Data
@TableName("policy_data_source")
public class PolicyDataSource {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 类型：API/RSS/WEBMAGIC/PYTHON
     */
    private String type;

    /**
     * JSON格式配置
     */
    private String config;

    /**
     * Python脚本路径（type=PYTHON时使用）
     */
    private String scriptPath;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * Cron表达式
     */
    private String cronExpr;

    /**
     * 上次抓取的最大发布日期（增量抓取游标）
     */
    private LocalDateTime lastPublishedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}