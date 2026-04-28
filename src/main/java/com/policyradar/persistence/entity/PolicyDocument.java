package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 政策文档表实体类
 *
 * 存储从各数据源抓取到的政策文档信息
 */
@Data
@TableName("policy_document")
public class PolicyDocument {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 原文链接
     */
    private String url;

    /**
     * 来源名称
     */
    private String source;

    /**
     * 发布机构
     */
    private String issuingAgency;

    /**
     * 文号
     */
    private String documentNumber;

    /**
     * 发布日期
     */
    private LocalDate publishDate;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 正文内容
     */
    private String content;

    /**
     * 灵活扩展字段（JSON格式）
     */
    private String metadata;

    /**
     * 内容哈希（去重用）
     */
    private String contentHash;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}