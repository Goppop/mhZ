package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 关键词订阅表实体类
 *
 * 存储用户配置的关键词订阅信息，用于匹配政策文档内容
 */
@Data
@TableName("policy_keyword")
public class PolicyKeyword {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订阅名称
     */
    private String name;

    /**
     * 主关键词
     */
    private String keyword;

    /**
     * 任意包含这些词（逗号分隔）
     */
    private String includeAny;

    /**
     * 必须包含所有词（逗号分隔）
     */
    private String includeAll;

    /**
     * 排除包含这些词（逗号分隔）
     */
    private String excludeAny;

    /**
     * 匹配字段，默认值：title,summary,content
     */
    private String matchFields;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否参与主动搜索（0=仅事后过滤，1=同时驱动检索）
     */
    private Boolean searchEnabled;

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