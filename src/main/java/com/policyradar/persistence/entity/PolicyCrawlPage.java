package com.policyradar.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * HTML 爬虫页面配置表
 *
 * LIST 页面负责发现列表 item，DETAIL 页面负责按详情页规则补齐正文等字段。
 */
@Data
@TableName("policy_crawl_page")
public class PolicyCrawlPage {

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
     * 页面角色：LIST/DETAIL
     */
    private String pageRole;

    /**
     * 页面配置名称
     */
    private String name;

    /**
     * 列表页 URL；详情页通常为空，由列表页字段 url 提供
     */
    private String url;

    /**
     * LIST 页面用于遍历 item 的 CSS 选择器
     */
    private String itemSelector;

    /**
     *  请求方法，第一版仅实现 GET
     */
    private String requestMethod;

    /**
     * 预留：请求头 JSON
     */
    private String headers;

    /**
     * 请求超时时间
     */
    private Integer timeoutMs;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 是否启用
     */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
